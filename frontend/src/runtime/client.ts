type ClientKind = 'web' | 'desktop' | 'android';

type NativeClientBridge = {
  kind?: ClientKind;
  platform?: string;
  defaultServerUrl?: string;
};

declare global {
  interface Window {
    wangDetectiveClient?: NativeClientBridge;
  }
}

const SERVER_BASE_KEY = 'wd:serverBaseUrl';

function envValue(name: string) {
  const env = import.meta.env as Record<string, string | undefined>;
  return env[name] || '';
}

export function clientKind(): ClientKind {
  const bridged = window.wangDetectiveClient?.kind;
  if (bridged === 'desktop' || bridged === 'android') {
    return bridged;
  }
  const configured = envValue('VITE_WD_CLIENT_KIND');
  if (configured === 'desktop' || configured === 'android') {
    return configured;
  }
  if (window.location.protocol === 'file:') {
    return 'desktop';
  }
  if (window.location.protocol === 'capacitor:') {
    return 'android';
  }
  return 'web';
}

export function isNativeClient() {
  return clientKind() !== 'web';
}

export function normalizeServerBaseUrl(value: string) {
  const raw = value.trim();
  if (!raw) {
    return '';
  }
  const withProtocol = /^https?:\/\//i.test(raw) ? raw : `https://${raw}`;
  try {
    const url = new URL(withProtocol);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return '';
    }
    const path = url.pathname.replace(/\/+$/, '');
    return `${url.origin}${path}`;
  } catch {
    return '';
  }
}

export function getStoredServerBaseUrl() {
  return normalizeServerBaseUrl(localStorage.getItem(SERVER_BASE_KEY) || '');
}

export function defaultServerBaseUrl() {
  return normalizeServerBaseUrl(
    window.wangDetectiveClient?.defaultServerUrl ||
    envValue('VITE_WD_SERVER_URL')
  );
}

export function getServerBaseUrl() {
  if (!isNativeClient()) {
    return '';
  }
  return getStoredServerBaseUrl() || defaultServerBaseUrl();
}

export function hasServerBaseUrl() {
  return !isNativeClient() || Boolean(getServerBaseUrl());
}

export function saveServerBaseUrl(value: string) {
  const normalized = normalizeServerBaseUrl(value);
  if (normalized) {
    localStorage.setItem(SERVER_BASE_KEY, normalized);
  } else {
    localStorage.removeItem(SERVER_BASE_KEY);
  }
  return normalized;
}

export function apiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${getServerBaseUrl()}${normalizedPath}`;
}

export function websocketUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const base = getServerBaseUrl() || window.location.origin;
  return `${base.replace(/^http/i, 'ws')}${normalizedPath}`;
}

export function currentServerLabel() {
  return getServerBaseUrl() || window.location.origin;
}
