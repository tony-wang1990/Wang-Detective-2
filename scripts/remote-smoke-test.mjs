#!/usr/bin/env node

import { spawn } from 'node:child_process';

const DEFAULT_TIMEOUT_MS = 20000;

function arg(name, fallback = '') {
  const prefix = `--${name}=`;
  const inline = process.argv.find((item) => item.startsWith(prefix));
  if (inline) return inline.slice(prefix.length);
  const index = process.argv.indexOf(`--${name}`);
  if (index >= 0 && process.argv[index + 1]) return process.argv[index + 1];
  return fallback;
}

const baseUrl = normalizeBase(arg('base', process.env.WANG_DETECTIVE_BASE_URL || process.env.BASE_URL || ''));
const username = arg('username', process.env.WANG_DETECTIVE_USERNAME || process.env.ADMIN_USERNAME || '');
const password = arg('password', process.env.WANG_DETECTIVE_PASSWORD || process.env.ADMIN_PASSWORD || '');
const timeoutMs = Number(arg('timeout', process.env.WANG_DETECTIVE_TIMEOUT_MS || DEFAULT_TIMEOUT_MS));
const insecure = arg('insecure', process.env.WANG_DETECTIVE_INSECURE || '') === '1';
const transport = arg('transport', process.env.WANG_DETECTIVE_TRANSPORT || 'auto');

if (insecure) {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
}

if (!baseUrl || !username || !password) {
  console.error('Usage: node scripts/remote-smoke-test.mjs --base https://example.com --username admin --password "***"');
  console.error('Or set WANG_DETECTIVE_BASE_URL, WANG_DETECTIVE_USERNAME, WANG_DETECTIVE_PASSWORD.');
  console.error('Optional: --transport auto|fetch|curl. Curl mode is useful when Node fetch cannot reach Cloudflare from the current host.');
  process.exit(2);
}

let token = '';
const results = [];
const WEB_ROUTES = [
  '/login',
  '/dashboard/home',
  '/dashboard/user',
  '/dashboard/createTask',
  '/dashboard/risk',
  '/dashboard/backups',
  '/dashboard/rescue',
  '/dashboard/clients',
  '/dashboard/features',
  '/dashboard/ops-terminal',
  '/dashboard/ociLog',
  '/dashboard/ops-audit',
  '/dashboard/sysCfg'
];

function normalizeBase(value) {
  return value.replace(/\/+$/, '');
}

function endpoint(path) {
  return `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
}

async function fetchWithTimeout(url, init = {}, timeout = timeoutMs) {
  if (transport === 'curl') {
    return curlWithTimeout(url, init, timeout);
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const started = Date.now();
  try {
    const response = await fetch(url, { ...init, signal: controller.signal });
    const elapsedMs = Date.now() - started;
    const text = await response.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      // Keep plain text.
    }
    return { response, body, elapsedMs };
  } catch (error) {
    const elapsedMs = Date.now() - started;
    if (error?.name === 'AbortError') {
      throw new Error(`timeout after ${Math.round(timeout / 1000)}s (${elapsedMs}ms)`);
    }
    if (transport === 'auto') {
      return curlWithTimeout(url, init, timeout);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

async function curlWithTimeout(url, init = {}, timeout = timeoutMs) {
  const started = Date.now();
  const curlBin = process.platform === 'win32' ? 'curl.exe' : 'curl';
  const args = [
    '-sS',
    '--connect-timeout',
    '8',
    '--max-time',
    String(Math.max(1, Math.ceil(timeout / 1000))),
    '-X',
    init.method || 'GET',
    '-w',
    '\n%{http_code}'
  ];
  if (insecure) {
    args.push('-k');
  }
  for (const [key, value] of Object.entries(init.headers || {})) {
    if (value !== undefined && value !== null && value !== '') {
      args.push('-H', `${key}: ${value}`);
    }
  }
  if (init.body !== undefined) {
    args.push('--data', String(init.body));
  }
  args.push(url);

  const { stdout, stderr, code } = await new Promise((resolve) => {
    const child = spawn(curlBin, args, { windowsHide: true });
    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk;
    });
    child.on('error', (error) => resolve({ stdout, stderr: error.message, code: 127 }));
    child.on('close', (code) => resolve({ stdout, stderr, code }));
  });

  const elapsedMs = Date.now() - started;
  if (code !== 0) {
    throw new Error(stderr.trim() || `curl exit ${code}`);
  }
  const marker = stdout.lastIndexOf('\n');
  const text = marker >= 0 ? stdout.slice(0, marker) : stdout;
  const status = Number(marker >= 0 ? stdout.slice(marker + 1).trim() : 0);
  let body = text;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    // Keep plain text.
  }
  return {
    response: {
      ok: status >= 200 && status < 300,
      status
    },
    body,
    elapsedMs
  };
}

function authHeaders() {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function messageFrom(body) {
  if (!body) return '';
  if (typeof body === 'string') return body.slice(0, 180);
  return body.msg || body.message || body.status || '';
}

async function check(name, method, path, body, validate = () => true) {
  const headers = {
    Accept: 'application/json',
    ...authHeaders()
  };
  const init = { method, headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  try {
    const { response, body: payload, elapsedMs } = await fetchWithTimeout(endpoint(path), init);
    const apiFailed = payload && typeof payload === 'object' && payload.success === false;
    const valid = response.ok && !apiFailed && validate(payload);
    results.push({
      name,
      ok: valid,
      status: response.status,
      elapsedMs,
      message: valid ? 'ok' : messageFrom(payload) || 'unexpected response'
    });
    return payload;
  } catch (error) {
    results.push({
      name,
      ok: false,
      status: '-',
      elapsedMs: 0,
      message: error instanceof Error ? error.message : String(error)
    });
    return null;
  }
}

function envelopeData(payload) {
  return payload && typeof payload === 'object' && 'data' in payload ? payload.data : payload;
}

async function main() {
  console.log(`Remote smoke test: ${baseUrl}`);

  await check('health', 'GET', '/actuator/health', undefined, (payload) => {
    const data = envelopeData(payload);
    return data?.status === 'UP';
  });
  await check('legacy-map-redirect', 'GET', '/ip-map.html', undefined, (payload) => {
    return typeof payload === 'string' && payload.includes('/dashboard/home');
  });
  await check('legacy-features-redirect', 'GET', '/wang-features.html', undefined, (payload) => {
    return typeof payload === 'string' && payload.includes('/dashboard/features');
  });
  await check('legacy-terminal-redirect', 'GET', '/ops-terminal.html', undefined, (payload) => {
    return typeof payload === 'string' && payload.includes('/dashboard/ops-terminal');
  });
  for (const route of WEB_ROUTES) {
    await check(`web-route:${route}`, 'GET', route, undefined, (payload) => {
      return typeof payload === 'string' && payload.includes('<div id="app"');
    });
  }

  const login = await check('login', 'POST', '/api/sys/login', {
    account: username,
    password
  }, (payload) => Boolean(envelopeData(payload)?.token));

  token = envelopeData(login)?.token || '';

  if (token) {
    await check('diagnostics', 'GET', '/api/v1/system/diagnostics', undefined, (payload) => Array.isArray(envelopeData(payload)?.checks));
    await check('version-info', 'GET', '/api/v1/system/version-info', undefined, (payload) => Boolean(envelopeData(payload)?.currentVersion));
    await check('client-packages', 'GET', '/api/v1/clients/packages', undefined, (payload) => {
      const packages = envelopeData(payload)?.packages;
      if (!Array.isArray(packages)) return false;
      const nativePackages = packages.filter((item) => item?.id === 'android' || item?.id === 'windows');
      return nativePackages.length === 2
        && nativePackages.every((item) => item.available === true && Boolean(item.downloadUrl));
    });
    await check('glance', 'GET', '/api/sys/glance', undefined, (payload) => typeof envelopeData(payload) === 'object');
    await check('sys-config', 'POST', '/api/sys/getSysCfg', undefined, (payload) => typeof envelopeData(payload) === 'object');
    const userPage = await check('oci-user-page', 'POST', '/api/oci/userPage', { currentPage: 1, pageSize: 5 }, (payload) => Array.isArray(envelopeData(payload)?.records));
    await check('task-page', 'POST', '/api/oci/createTaskPage', { currentPage: 1, pageSize: 5 }, (payload) => Array.isArray(envelopeData(payload)?.records));
    await check('audit-recent', 'GET', '/api/ops/audit/recent?limit=5', undefined, (payload) => Array.isArray(envelopeData(payload)));
    await check('audit-search', 'GET', '/api/ops/audit/search?limit=5', undefined, (payload) => Array.isArray(envelopeData(payload)));
    await check('audit-export', 'GET', '/api/ops/audit/export?limit=5', undefined, (payload) => typeof payload === 'string');
    await check('logs-recent', 'GET', '/api/v1/logs/recent?limit=20', undefined, (payload) => Array.isArray(envelopeData(payload)?.lines));
    await check('ops-ssh-hosts', 'GET', '/api/ops/ssh/hosts', undefined, (payload) => Array.isArray(envelopeData(payload)));
    await check('ops-ssh-sessions', 'GET', '/api/ops/ssh/sessions', undefined, (payload) => Array.isArray(envelopeData(payload)));
    await check('ops-templates', 'GET', '/api/ops/templates', undefined, (payload) => Array.isArray(envelopeData(payload)));
    await check('backup-local', 'GET', '/api/v1/backups/local', undefined, (payload) => Array.isArray(envelopeData(payload)?.backups) || Array.isArray(envelopeData(payload)));
    await check('backup-schedule-plan', 'GET', '/api/v1/backups/schedule-plan', undefined, (payload) => typeof envelopeData(payload) === 'object');
    await check('rescue-overview', 'GET', '/api/rescue/overview', undefined, (payload) => typeof envelopeData(payload) === 'object');
    await check('rescue-light-script', 'GET', '/api/rescue/light-script', undefined, (payload) => typeof envelopeData(payload) === 'string' && envelopeData(payload).includes('bash'));
    await check('rescue-netboot-script', 'GET', '/api/rescue/netboot-script?mode=ipxe', undefined, (payload) => typeof envelopeData(payload) === 'string' && envelopeData(payload).toLowerCase().includes('netboot'));
    await check('oci-risk', 'GET', '/api/v1/oci/risk?maxConfigs=1', undefined, (payload) => {
      const data = envelopeData(payload);
      return data && Array.isArray(data.configs) && data.configs.every((config) => !config.portExposures || Array.isArray(config.portExposures));
    });

    const firstConfig = envelopeData(userPage)?.records?.find((item) => item?.id);
    if (firstConfig?.id) {
      const vcnPage = await check('vcn-page', 'POST', '/api/vcn/page', {
        ociCfgId: firstConfig.id,
        currentPage: 1,
        pageSize: 10,
        cleanReLaunch: true
      }, (payload) => Array.isArray(envelopeData(payload)?.records));
      const firstVcn = envelopeData(vcnPage)?.records?.find((item) => item?.id);
      if (firstVcn?.id) {
        const securityBase = {
          ociCfgId: firstConfig.id,
          vcnId: firstVcn.id,
          currentPage: 1,
          pageSize: 20,
          cleanReLaunch: false
        };
        await check('security-rules-ingress', 'POST', '/api/securityRule/page', {
          ...securityBase,
          type: 0
        }, (payload) => Array.isArray(envelopeData(payload)?.records));
        await check('security-rules-egress', 'POST', '/api/securityRule/page', {
          ...securityBase,
          type: 1
        }, (payload) => Array.isArray(envelopeData(payload)?.records));
      }
    }
  }

  console.log('\nResult:');
  for (const item of results) {
    const state = item.ok ? 'PASS' : 'FAIL';
    console.log(`${state.padEnd(4)} ${String(item.status).padEnd(4)} ${String(item.elapsedMs).padStart(6)}ms ${item.name} - ${item.message}`);
  }

  const failed = results.filter((item) => !item.ok);
  if (failed.length) {
    console.error(`\nFailed ${failed.length}/${results.length} checks.`);
    process.exit(1);
  }
  console.log(`\nAll ${results.length} checks passed.`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack || error.message : String(error));
  process.exit(1);
});
