<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { CheckCircle2, Clock3, Download, Filter, RefreshCw, Search, ShieldCheck, XCircle } from 'lucide-vue-next';
import { opsGet } from '../api/http';
import { apiUrl } from '../runtime/client';
import { filenameFromDisposition, saveBlob } from '../runtime/fileTransfer';

type AuditLog = {
  id?: string;
  userId?: string;
  username?: string;
  operation?: string;
  target?: string;
  details?: string;
  success?: boolean;
  errorMessage?: string;
  ipAddress?: string;
  userAgent?: string;
  createTime?: string;
};

const logs = ref<AuditLog[]>([]);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const statusFilter = ref<'all' | 'success' | 'failure'>('all');
const limit = ref(100);
const selectedId = ref('');
const lastLoadedAt = ref('');

function buildQuery(exportMode = false) {
  const params = new URLSearchParams();
  params.set('limit', String(exportMode ? Math.max(limit.value, 1000) : limit.value));
  if (keyword.value.trim()) {
    params.set('keyword', keyword.value.trim());
  }
  if (statusFilter.value === 'success') {
    params.set('success', 'true');
  } else if (statusFilter.value === 'failure') {
    params.set('success', 'false');
  }
  return params.toString();
}

const filteredLogs = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  return logs.value.filter((item) => {
    const statusMatched =
      statusFilter.value === 'all' ||
      (statusFilter.value === 'success' && item.success === true) ||
      (statusFilter.value === 'failure' && item.success === false);
    const haystack = [
      item.operation,
      item.target,
      item.username,
      item.userId,
      item.ipAddress,
      item.details,
      item.errorMessage,
      item.userAgent
    ].join(' ').toLowerCase();
    return statusMatched && (!word || haystack.includes(word));
  });
});

const selectedLog = computed(() => filteredLogs.value.find((item) => item.id === selectedId.value) || filteredLogs.value[0]);
const successCount = computed(() => logs.value.filter((item) => item.success === true).length);
const failureCount = computed(() => logs.value.filter((item) => item.success === false).length);
const uniqueIpCount = computed(() => new Set(logs.value.map((item) => item.ipAddress).filter(Boolean)).size);
const latestTime = computed(() => formatTime(logs.value[0]?.createTime) || '-');

function statusText(item: AuditLog) {
  if (item.success === true) return '成功';
  if (item.success === false) return '失败';
  return '未知';
}

function statusClass(item: AuditLog) {
  if (item.success === true) return 'success';
  if (item.success === false) return 'warning';
  return 'muted';
}

function formatTime(value?: string) {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized;
}

function shortText(value?: string, fallback = '-') {
  const text = String(value || '').trim();
  if (!text) return fallback;
  return text.length > 96 ? `${text.slice(0, 96)}...` : text;
}

function detailText(item?: AuditLog) {
  const source = item?.details || item?.errorMessage || '';
  if (!source) return '暂无详情';
  try {
    return JSON.stringify(JSON.parse(source), null, 2);
  } catch {
    return source;
  }
}

async function loadAudits() {
  loading.value = true;
  error.value = '';
  try {
    const res = await opsGet<AuditLog[]>(`/audit/search?${buildQuery(false)}`);
    logs.value = res.data || [];
    selectedId.value = logs.value[0]?.id || '';
    lastLoadedAt.value = new Date().toLocaleTimeString();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '读取审计日志失败';
  } finally {
    loading.value = false;
  }
}

async function exportAudits() {
  loading.value = true;
  error.value = '';
  try {
    const response = await fetch(apiUrl(`/api/ops/audit/export?${buildQuery(true)}`), {
      headers: sessionStorage.getItem('token') ? { Authorization: `Bearer ${sessionStorage.getItem('token')}` } : {}
    });
    if (!response.ok) {
      throw new Error(await response.text() || `export ${response.status}`);
    }
    const blob = await response.blob();
    const name = filenameFromDisposition(
      response.headers.get('Content-Disposition') || undefined,
      `wang-detective-audit-${Date.now()}.csv`
    );
    await saveBlob(blob, name);
  } catch (err) {
    error.value = err instanceof Error ? err.message : '导出审计日志失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadAudits);
</script>

<template>
  <section class="wd-page wd-audit-page">
    <div class="wd-page-title">
      <div>
        <h1>操作审计</h1>
        <p>关键运维动作、SSH/SFTP 操作、执行结果和来源 IP 的集中追踪。</p>
      </div>
      <div class="wd-actions">
        <button type="button" class="ghost" @click="exportAudits" :disabled="loading">
          <Download :size="16" />导出 CSV
        </button>
        <button type="button" @click="loadAudits" :disabled="loading">
          <RefreshCw :size="16" />{{ loading ? '刷新中' : '刷新' }}
        </button>
      </div>
    </div>

    <section class="wd-log-summary">
      <article class="wd-card wd-stat-card">
        <span>审计记录</span>
        <strong><ShieldCheck :size="20" />{{ logs.length }}</strong>
      </article>
      <article class="wd-card wd-stat-card">
        <span>成功 / 失败</span>
        <strong><CheckCircle2 :size="20" />{{ successCount }} / {{ failureCount }}</strong>
      </article>
      <article class="wd-card wd-stat-card">
        <span>来源 IP</span>
        <strong><Filter :size="20" />{{ uniqueIpCount }}</strong>
      </article>
      <article class="wd-card wd-stat-card">
        <span>最近操作</span>
        <strong><Clock3 :size="20" />{{ latestTime }}</strong>
      </article>
    </section>

    <p v-if="error" class="wd-error-line">{{ error }}</p>

    <section class="wd-audit-layout">
      <div class="wd-card wd-table-card">
        <header>
          <h2><ShieldCheck :size="17" /> 审计流水</h2>
          <div class="wd-table-tools">
            <label class="wd-inline-search">
              <Search :size="15" />
              <input v-model="keyword" placeholder="搜索操作、目标、用户、IP..." @keyup.enter="loadAudits" />
            </label>
            <select v-model="statusFilter" @change="loadAudits">
              <option value="all">全部状态</option>
              <option value="success">成功</option>
              <option value="failure">失败</option>
            </select>
            <select v-model.number="limit" @change="loadAudits">
              <option :value="50">最近 50 条</option>
              <option :value="100">最近 100 条</option>
              <option :value="200">最近 200 条</option>
              <option :value="500">最近 500 条</option>
            </select>
          </div>
        </header>

        <div class="wd-table-scroll">
          <table class="wd-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>状态</th>
                <th>操作</th>
                <th>目标</th>
                <th>用户 / IP</th>
                <th>详情</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in filteredLogs"
                :key="item.id || `${item.createTime}-${item.operation}`"
                :class="{ selected: selectedLog?.id === item.id }"
                @click="selectedId = item.id || ''"
              >
                <td class="wd-nowrap">{{ formatTime(item.createTime) || '-' }}</td>
                <td><span class="wd-badge" :class="statusClass(item)">{{ statusText(item) }}</span></td>
                <td><b>{{ item.operation || '-' }}</b></td>
                <td>{{ shortText(item.target) }}</td>
                <td>
                  <b>{{ item.username || item.userId || 'web' }}</b>
                  <small>{{ item.ipAddress || '-' }}</small>
                </td>
                <td>{{ shortText(item.details || item.errorMessage) }}</td>
              </tr>
              <tr v-if="!filteredLogs.length">
                <td colspan="6" class="wd-empty">暂无匹配的审计记录</td>
              </tr>
            </tbody>
          </table>
        </div>

        <footer class="wd-table-footer">
          当前显示 {{ filteredLogs.length }} 条，最近刷新 {{ lastLoadedAt || '-' }}
        </footer>
      </div>

      <aside class="wd-card wd-audit-detail">
        <header>
          <h2>
            <component :is="selectedLog?.success === false ? XCircle : CheckCircle2" :size="17" />
            记录详情
          </h2>
          <span v-if="selectedLog" class="wd-badge" :class="statusClass(selectedLog)">{{ statusText(selectedLog) }}</span>
        </header>

        <template v-if="selectedLog">
          <dl>
            <dt>操作时间</dt>
            <dd>{{ formatTime(selectedLog.createTime) || '-' }}</dd>
            <dt>操作类型</dt>
            <dd>{{ selectedLog.operation || '-' }}</dd>
            <dt>操作目标</dt>
            <dd>{{ selectedLog.target || '-' }}</dd>
            <dt>操作者</dt>
            <dd>{{ selectedLog.username || selectedLog.userId || 'web' }}</dd>
            <dt>来源 IP</dt>
            <dd>{{ selectedLog.ipAddress || '-' }}</dd>
            <dt>User Agent</dt>
            <dd>{{ selectedLog.userAgent || '-' }}</dd>
          </dl>
          <pre class="wd-audit-code">{{ detailText(selectedLog) }}</pre>
        </template>

        <p v-else class="wd-empty">暂无可查看的记录</p>
      </aside>
    </section>
  </section>
</template>
