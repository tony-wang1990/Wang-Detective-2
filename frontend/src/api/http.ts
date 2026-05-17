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

function authHeaders(): HeadersInit {
  const token = sessionStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
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
  const response = await fetch(`/api${url}`, {
    headers: authHeaders()
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function apiPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const response = await fetch(`/api${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify(body)
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function apiForm<T>(url: string, form: FormData): Promise<ApiEnvelope<T>> {
  const response = await fetch(`/api${url}`, {
    method: 'POST',
    headers: authHeaders(),
    body: form
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function opsGet<T>(url: string): Promise<ApiEnvelope<T>> {
  const response = await fetch(`/api/ops${url}`, {
    headers: authHeaders()
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function opsPost<T>(url: string, body: unknown): Promise<ApiEnvelope<T>> {
  const response = await fetch(`/api/ops${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify(body)
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function opsDelete<T>(url: string): Promise<ApiEnvelope<T>> {
  const response = await fetch(`/api/ops${url}`, {
    method: 'DELETE',
    headers: authHeaders()
  });
  return parseResponse<ApiEnvelope<T>>(response, url);
}

export async function opsDownload(url: string, body: unknown): Promise<{ blob: Blob; filename?: string }> {
  const response = await fetch(`/api/ops${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders()
    },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${url} ${response.status}`);
  }
  return {
    blob: await response.blob(),
    filename: response.headers.get('Content-Disposition') || undefined
  };
}

export async function opsUpload(path: string, hostId: string, file: File): Promise<ApiEnvelope<void>> {
  const form = new FormData();
  form.append('hostId', hostId);
  form.append('path', path);
  form.append('file', file);
  const response = await fetch('/api/ops/sftp/upload', {
    method: 'POST',
    headers: authHeaders(),
    body: form
  });
  return parseResponse<ApiEnvelope<void>>(response, '/sftp/upload');
}

export async function rawGet<T>(url: string): Promise<T> {
  const response = await fetch(url, {
    headers: authHeaders()
  });
  return parseResponse<T>(response, url);
}

export async function getHealth(): Promise<HealthData> {
  const response = await fetch('/actuator/health', {
    headers: authHeaders()
  });
  if (!response.ok) {
    throw new Error(`health ${response.status}`);
  }
  return response.json();
}
