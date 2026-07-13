import { apiUrl } from '../runtime/client';

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
export type TransferProgress = {
  loaded: number;
  total?: number;
  percent?: number;
};

let activeRequests = 0;
let redirectingToLogin = false;

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

function handleUnauthorized() {
  sessionStorage.clear();
  if (!redirectingToLogin) {
    redirectingToLogin = true;
    notifyGlobal('登录已过期，请重新登录', 'error');
  }
  if (!window.location.href.includes('/login')) {
    window.location.href = window.location.href.includes('#') ? '#/login' : '/login';
  }
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
      handleUnauthorized();
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
  const requestUrl = apiUrl(`/api${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function apiPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const requestUrl = apiUrl(`/api${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
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

export async function apiPostLong<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const requestUrl = apiUrl(`/api${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders()
      },
      body: JSON.stringify(body)
    }, 180000);
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function apiDownload(url: string): Promise<{ blob: Blob; filename?: string }> {
  const requestUrl = apiUrl(`/api${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      headers: authHeaders()
    }, DOWNLOAD_TIMEOUT_MS);
    if (!response.ok) {
      if (response.status === 401) {
        handleUnauthorized();
      }
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

export async function apiForm<T>(url: string, form: FormData): Promise<ApiEnvelope<T>> {
  const requestUrl = apiUrl(`/api${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
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
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
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

export async function opsPut<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      method: 'PUT',
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
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      method: 'DELETE',
      headers: authHeaders()
    });
    return await parseResponse<ApiEnvelope<T>>(response, url);
  } finally {
    done();
  }
}

export async function opsDownload(url: string, body: unknown): Promise<{ blob: Blob; filename?: string }> {
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
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

export async function opsDownloadWithProgress(
  url: string,
  body: unknown,
  onProgress?: (progress: TransferProgress) => void
): Promise<{ blob: Blob; filename?: string }> {
  const requestUrl = apiUrl(`/api/ops${url}`);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
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

    const total = Number(response.headers.get('Content-Length') || 0);
    const filename = response.headers.get('Content-Disposition') || undefined;
    if (!response.body) {
      const blob = await response.blob();
      onProgress?.({ loaded: blob.size, total: blob.size, percent: 100 });
      return { blob, filename };
    }

    const reader = response.body.getReader();
    const chunks: BlobPart[] = [];
    let loaded = 0;
    while (true) {
      const { done: readerDone, value } = await reader.read();
      if (readerDone) break;
      if (value) {
        chunks.push(value.buffer.slice(value.byteOffset, value.byteOffset + value.byteLength) as ArrayBuffer);
        loaded += value.byteLength;
        onProgress?.({
          loaded,
          total: total || undefined,
          percent: total ? Math.min(100, Math.round((loaded / total) * 100)) : undefined
        });
      }
    }

    const blob = new Blob(chunks, {
      type: response.headers.get('Content-Type') || 'application/octet-stream'
    });
    onProgress?.({ loaded, total: total || loaded, percent: 100 });
    return { blob, filename };
  } finally {
    done();
  }
}

export async function opsUpload(path: string, hostId: string, file: File): Promise<ApiEnvelope<void>> {
  const form = new FormData();
  form.append('hostId', hostId);
  form.append('path', path);
  form.append('file', file);
  const requestUrl = apiUrl('/api/ops/sftp/upload');
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      method: 'POST',
      headers: authHeaders(),
      body: form
    }, FORM_TIMEOUT_MS);
    return await parseResponse<ApiEnvelope<void>>(response, '/sftp/upload');
  } finally {
    done();
  }
}

export function opsUploadWithProgress(
  path: string,
  hostId: string,
  file: File,
  onProgress?: (progress: TransferProgress) => void
): Promise<ApiEnvelope<void>> {
  const form = new FormData();
  form.append('hostId', hostId);
  form.append('path', path);
  form.append('file', file);
  const requestUrl = apiUrl('/api/ops/sftp/upload');
  const done = beginNetwork(requestUrl);
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', requestUrl);
    xhr.timeout = FORM_TIMEOUT_MS;
    const token = sessionStorage.getItem('token');
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }

    xhr.upload.onprogress = (event) => {
      onProgress?.({
        loaded: event.loaded,
        total: event.lengthComputable ? event.total : file.size,
        percent: event.lengthComputable && event.total
          ? Math.min(100, Math.round((event.loaded / event.total) * 100))
          : undefined
      });
    };

    xhr.onload = () => {
      done();
      let payload: ApiEnvelope<void> | undefined;
      try {
        payload = xhr.responseText ? JSON.parse(xhr.responseText) : undefined;
      } catch {
        // Keep payload undefined and use status text below.
      }
      if (xhr.status >= 200 && xhr.status < 300 && payload?.success !== false) {
        onProgress?.({ loaded: file.size, total: file.size, percent: 100 });
        resolve(payload || { success: true, data: undefined as void });
        return;
      }
      if (xhr.status === 401) {
        handleUnauthorized();
      }
      reject(new Error(payload?.msg || payload?.message || xhr.responseText || `upload ${xhr.status}`));
    };

    xhr.onerror = () => {
      done();
      reject(new Error('上传失败：网络连接异常'));
    };
    xhr.ontimeout = () => {
      done();
      reject(new Error(`上传超时：超过 ${Math.round(FORM_TIMEOUT_MS / 1000)} 秒无响应`));
    };
    xhr.onabort = () => {
      done();
      reject(new Error('上传已取消'));
    };
    xhr.send(form);
  });
}

export async function rawGet<T>(url: string): Promise<T> {
  const requestUrl = url.startsWith('http') ? url : apiUrl(url);
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      headers: authHeaders()
    });
    return await parseResponse<T>(response, url);
  } finally {
    done();
  }
}

export async function getHealth(): Promise<HealthData> {
  const requestUrl = apiUrl('/actuator/health');
  const done = beginNetwork(requestUrl);
  try {
    const response = await fetchWithTimeout(requestUrl, {
      headers: {}
    }, HEALTH_TIMEOUT_MS);
    if (!response.ok) {
      throw new Error(`health ${response.status}`);
    }
    return response.json();
  } finally {
    done();
  }
}
