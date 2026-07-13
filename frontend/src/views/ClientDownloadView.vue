<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  AlertTriangle,
  AppWindow,
  CheckCircle2,
  Copy,
  Download,
  ExternalLink,
  Monitor,
  RefreshCw,
  Server,
  ShieldCheck,
  Smartphone
} from 'lucide-vue-next';
import { apiGet } from '../api/http';
import { currentServerLabel } from '../runtime/client';
import { openExternalUrl } from '../runtime/fileTransfer';

type ClientPackage = {
  id: 'android' | 'windows' | 'web' | string;
  name: string;
  platform?: string;
  version?: string;
  fileName?: string;
  downloadUrl?: string;
  available?: boolean;
  status?: string;
  sizeBytes?: number;
  updatedAt?: string;
  sha256?: string;
  notes?: string[];
};

type ClientPackagePayload = {
  serverBaseUrl?: string;
  apiBaseUrl?: string;
  downloadBaseUrl?: string;
  version?: string;
  generatedAt?: string;
  packages?: ClientPackage[];
};

const loading = ref(false);
const error = ref('');
const copied = ref('');
const payload = ref<ClientPackagePayload>({
  serverBaseUrl: currentServerLabel(),
  packages: []
});

const packages = computed(() => payload.value.packages || []);
const serverBaseUrl = computed(() => payload.value.serverBaseUrl || currentServerLabel());
const apiBaseUrl = computed(() => payload.value.apiBaseUrl || `${serverBaseUrl.value}/api`);
const generatedAt = computed(() => formatTime(payload.value.generatedAt));

function iconFor(id: string) {
  if (id === 'android') return Smartphone;
  if (id === 'windows') return Monitor;
  return AppWindow;
}

function formatBytes(value?: number) {
  if (!value) return '未知大小';
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatTime(value?: string) {
  if (!value) return '未记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function shortSha(value?: string) {
  return value ? `${value.slice(0, 12)}...` : '未生成';
}

function statusText(item: ClientPackage) {
  if (item.id === 'web') return '在线可用';
  if (item.available) return '可下载';
  return '等待上传安装包';
}

function statusClass(item: ClientPackage) {
  if (item.id === 'web' || item.available) return 'success';
  return 'warning';
}

async function copyText(value: string, key: string) {
  await navigator.clipboard.writeText(value);
  copied.value = key;
  window.setTimeout(() => {
    if (copied.value === key) copied.value = '';
  }, 1800);
}

async function openPackage(item: ClientPackage) {
  if (!item.downloadUrl) return;
  await openExternalUrl(item.downloadUrl);
}

async function loadPackages() {
  loading.value = true;
  error.value = '';
  try {
    const res = await apiGet<ClientPackagePayload>('/v1/clients/packages');
    payload.value = res.data || {};
  } catch (err) {
    error.value = err instanceof Error ? err.message : '读取客户端安装包信息失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadPackages);
</script>

<template>
  <section class="wd-page wd-clients-page">
    <div class="wd-page-title">
      <div>
        <h1>客户端下载中心</h1>
        <p>Android APP、Windows 客户端和 Web 控制台连接同一套 VPS API，业务数据统一同步。</p>
      </div>
      <div class="wd-actions">
        <button type="button" class="ghost" :disabled="loading" @click="loadPackages">
          <RefreshCw :size="16" />{{ loading ? '检查中' : '重新检查' }}
        </button>
      </div>
    </div>

    <article class="wd-card wd-client-server-card">
      <header>
        <h2><Server :size="18" />客户端登录服务器地址</h2>
        <span class="wd-badge success">API</span>
      </header>
      <p>手机 APP 和桌面客户端首次登录时填写下面这个地址。</p>
      <div class="wd-client-url">
        <code>{{ serverBaseUrl }}</code>
        <button type="button" class="ghost" @click="copyText(serverBaseUrl, 'server')">
          <Copy :size="15" />{{ copied === 'server' ? '已复制' : '复制' }}
        </button>
      </div>
      <small>实际接口前缀：{{ apiBaseUrl }}</small>
    </article>

    <p v-if="error" class="wd-error">{{ error }}</p>

    <section class="wd-client-grid">
      <article v-for="item in packages" :key="item.id" class="wd-card wd-client-card">
        <header>
          <div>
            <component :is="iconFor(item.id)" :size="23" />
            <div>
              <h2>{{ item.name }}</h2>
              <span>{{ item.platform || '-' }}</span>
            </div>
          </div>
          <em class="wd-badge" :class="statusClass(item)">
            <CheckCircle2 v-if="item.id === 'web' || item.available" :size="13" />
            <AlertTriangle v-else :size="13" />
            {{ statusText(item) }}
          </em>
        </header>

        <dl>
          <div>
            <dt>版本</dt>
            <dd>{{ item.version || payload.version || '-' }}</dd>
          </div>
          <div>
            <dt>大小</dt>
            <dd>{{ item.id === 'web' ? '无需安装' : formatBytes(item.sizeBytes) }}</dd>
          </div>
          <div>
            <dt>发布时间</dt>
            <dd>{{ item.updatedAt ? formatTime(item.updatedAt) : generatedAt }}</dd>
          </div>
          <div>
            <dt>SHA256</dt>
            <dd>{{ shortSha(item.sha256) }}</dd>
          </div>
        </dl>

        <ul>
          <li v-for="note in item.notes || []" :key="note">{{ note }}</li>
        </ul>

        <footer>
          <button type="button" :disabled="item.id !== 'web' && !item.available" @click="openPackage(item)">
            <Download v-if="item.id !== 'web'" :size="16" />
            <ExternalLink v-else :size="16" />
            {{ item.id === 'web' ? '打开 Web 控制台' : '下载安装包' }}
          </button>
          <button
            v-if="item.id !== 'web'"
            type="button"
            class="ghost"
            :disabled="!item.sha256"
            @click="copyText(item.sha256 || '', item.id)"
          >
            <ShieldCheck :size="16" />{{ copied === item.id ? '已复制' : '校验 SHA256' }}
          </button>
        </footer>
      </article>
    </section>
  </section>
</template>

<style scoped>
.wd-clients-page {
  gap: 24px;
}

.wd-client-server-card p {
  margin: 0 0 16px;
  color: var(--wd-muted);
}

.wd-client-server-card header h2,
.wd-client-card header h2 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.wd-client-url {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--wd-line);
  border-radius: 8px;
  background: var(--wd-surface-2);
}

.wd-client-url code {
  flex: 1;
  overflow-wrap: anywhere;
  color: var(--wd-text);
}

.wd-client-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.wd-client-card {
  display: flex;
  min-height: 420px;
  flex-direction: column;
}

.wd-client-card header {
  align-items: flex-start;
}

.wd-client-card header > div {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.wd-client-card header svg {
  color: var(--wd-accent);
  flex: 0 0 auto;
}

.wd-client-card header span {
  color: var(--wd-muted);
  font-size: 13px;
}

.wd-client-card dl {
  display: grid;
  gap: 10px;
  margin: 18px 0;
}

.wd-client-card dl div {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  gap: 12px;
}

.wd-client-card dt {
  color: var(--wd-muted);
}

.wd-client-card dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.wd-client-card ul {
  margin: 0 0 18px;
  padding-left: 18px;
  color: var(--wd-muted);
  line-height: 1.8;
}

.wd-client-card footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: auto;
}

.wd-client-card footer button,
.wd-client-url button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  padding: 0 14px;
  background: var(--wd-accent);
  color: #fff;
  font-weight: 700;
}

.wd-client-card footer button.ghost,
.wd-client-url button.ghost {
  border: 1px solid var(--wd-line);
  background: transparent;
  color: var(--wd-text);
}

@media (max-width: 1180px) {
  .wd-client-grid {
    grid-template-columns: 1fr;
  }

  .wd-client-card {
    min-height: 0;
  }
}

@media (max-width: 720px) {
  .wd-client-url,
  .wd-client-card footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
