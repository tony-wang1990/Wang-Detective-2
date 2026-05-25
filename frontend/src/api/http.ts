export interface ApiEnvelope<T> {
  success: boolean;
  msg?: string;
  data: T;
  code?: number;
  message?: string;
}

export interface LoginResponse {
  token: string;
  currentVersion?: string;
  latestVersion?: string;
}

export interface GlanceData {
  users?: number;
  tasks?: number;
  regions?: number;
  days?: number;
  currentVersion?: string;
  cities?: Array<{
    lat?: number;
    lng?: number;
    country?: string;
    area?: string;
    city?: string;
    org?: string;
    asn?: string;
    count?: number;
  }>;
}

export interface HealthData {
  status?: string;
  databaseConnectivity?: boolean;
  memoryStatus?: boolean;
  usedMemoryBytes?: number;
  maxMemoryBytes?: number;
  uptimeSeconds?: number;
  version?: string;
}

export type PageResult<T = Record<string, unknown>> = {
  records?: T[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
};

type ToastKind = 'success' | 'error' | 'info';

let activeRequests = 0;

const DEFAULT_TIMEOUT_MS = 45000;
const HEALTH_TIMEOUT_MS = 12000;
const FORM_TIMEOUT_MS = 90000;
const DOWNLOAD_TIMEOUT_MS = 120000;

function emit(name: string, detail: unknown) {
  window.dispatchEvent(new CustomEvent(name, { detail }));
}

function beginNetwork(url: string) {
  activeRequests += 1;
  emit('wd:network', { active: true, count: activeRequests, url });
  return () => {
    activeRequests = Math.max(0, activeRequests - 1);
    emit('wd:network', { active: activeRequests > 0, count: activeRequests, url });
  };
}

export function notifyGlobal(message: string, kind: ToastKind = 'info') {
  emit('wd:toast', { message, kind });
}

function authHeaders(): HeadersInit {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function fetchWithTimeout(url: string, init: RequestInit = {}, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, {
      ...init,
      signal: controller.signal
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error(`请求超时：${url} 超过 ${Math.round(timeoutMs / 1000)} 秒无响应，请检查服务健康、反向代理或 Cloudflare 源站连接。`);
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

async function parseResponse<T>(response: Response, url: string): Promise<T> {
  const text = await response.text();
  let payload: unknown = undefined;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }
  if (!response.ok) {
    const message = typeof payload === 'object' && payload
      ? (payload as { msg?: string; message?: string }).msg || (payload as { msg?: string; message?: string }).message
      : String(payload || '');
    if (response.status === 401) {
      sessionStorage.clear();
      notifyGlobal('登录已过期，请重新登录', 'error');
      window.setTimeout(() => {
        if (!window.location.pathname.includes('/login')) {
          window.location.href = '/login';
        }
      }, 600);
    }
    throw new Error(message || `${url} ${response.status}`);
  }
  if (
    typeof payload === 'object' &&
    payload &&
    'success' in payload &&
    (payload as { success?: boolean }).success === false
  ) {
    throw new Error((payload as { msg?: string; message?: string }).msg || (payload as { msg?: string; message?: string }).message || '请求失败');
  }
  return payload as T;
}

export async function apiGet<T>(url: string): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api${url}`);
  try {
    const response = await fetchWithTimeout(`/api${url}`, {
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function apiPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api${url}`);
  try {
    const response = await fetchWithTimeout(`/api${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders()
      },
      body: JSON.stringify(body)
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function apiForm<T>(url: string, form: FormData): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api${url}`);
  try {
    const response = await fetchWithTimeout(`/api${url}`, {
      method: 'POST',
      headers: authHeaders(),
      body: form
    }, FORM_TIMEOUT_MS);
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsGet<T>(url: string): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api/ops${url}`);
  try {
    const response = await fetchWithTimeout(`/api/ops${url}`, {
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api/ops${url}`);
  try {
    const response = await fetchWithTimeout(`/api/ops${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders()
      },
      body: JSON.stringify(body)
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsDelete<T>(url: string): Promise<ApiEnvelope<T>> {
  const done = beginNetwork(`/api/ops${url}`);
  try {
    const response = await fetchWithTimeout(`/api/ops${url}`, {
      method: 'DELETE',
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsDownload(url: string, body: unknown): Promise<{ blob: Blob; filename?: string }> {
  const done = beginNetwork(`/api/ops${url}`);
  try {
    const response = await fetchWithTimeout(`/api/ops${url}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders()
      },
      body: JSON.stringify(body)
    }, DOWNLOAD_TIMEOUT_MS);
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `${url} ${response.status}`);
    }
    return {
      blob: await response.blob(),
      filename: response.headers.get('Content-Disposition') || undefined
    };
  } finally {
    done();
  }
}

export async function opsUpload(path: string, hostId: string, file: File): Promise<ApiEnvelope<void>> {
  const form = new FormData();
  form.append('hostId', hostId);
  form.append('path', path);
  form.append('file', file);
  const done = beginNetwork('/api/ops/sftp/upload');
  try {
    const response = await fetchWithTimeout('/api/ops/sftp/upload', {
      method: 'POST',
      headers: authHeaders(),
      body: form
    }, FORM_TIMEOUT_MS);
    return await parseResponse<ApiEnvelope<void>>(response, '/sftp/upload');
  } finally {
    done();
  }
}

export async function rawGet<T>(url: string): Promise<T> {
  const done = beginNetwork(url);
  try {
    const response = await fetchWithTimeout(url, {
      headers: authHeaders()
    });
    return await parseResponse<T>(response, url);
  } finally {
    done();
  }
}

export async function getHealth(): Promise<HealthData> {
  const done = beginNetwork('/actuator/health');
  try {
    const response = await fetchWithTimeout('/actuator/health', {
      headers: authHeaders()
    }, HEALTH_TIMEOUT_MS);
    if (!response.ok) {
      throw new Error(`health ${response.status}`);
    }
    return response.json();
  } finally {
    done();
  }
}
