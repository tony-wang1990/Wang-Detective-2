<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { FileText, Pause, Play, RefreshCw, Search, Trash2 } from 'lucide-vue-next';
import { apiGet, notifyGlobal } from '../api/http';
import { websocketUrl } from '../runtime/client';

const lines = ref<string[]>([]);
const status = ref<'idle' | 'connecting' | 'open' | 'closed' | 'error'>('idle');
const paused = ref(false);
const autoScroll = ref(true);
const keyword = ref('');
const level = ref('');
const lineLimit = ref(500);
const lastMessageAt = ref('');
const loadingHistory = ref(false);
const terminalRef = ref<HTMLElement | null>(null);
let ws: WebSocket | null = null;

const visibleLines = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  return lines.value.filter((line) => {
    const levelMatched = !level.value || line.toUpperCase().includes(level.value);
    const keywordMatched = !word || line.toLowerCase().includes(word);
    return levelMatched && keywordMatched;
  });
});

const stats = computed(() => {
  const summary = { info: 0, warn: 0, error: 0 };
  lines.value.forEach((line) => {
    const text = line.toUpperCase();
    if (text.includes('ERROR')) summary.error += 1;
    else if (text.includes('WARN')) summary.warn += 1;
    else if (text.includes('INFO')) summary.info += 1;
  });
  return summary;
});

const statusText = computed(() => {
  const map = {
    idle: '未连接',
    connecting: '连接中',
    open: paused.value ? '已暂停显示' : '实时推送中',
    closed: '已断开',
    error: '连接异常'
  };
  return map[status.value];
});

const statusClass = computed(() => {
  if (status.value === 'open') return paused.value ? 'warning' : 'success';
  if (status.value === 'error') return 'danger';
  return 'muted';
});

function socketUrl() {
  const token = sessionStorage.getItem('token') || '';
  if (!token) return '';
  return websocketUrl(`/logs?token=${encodeURIComponent(token)}`);
}

function trimLines() {
  const limit = Number(lineLimit.value || 500);
  if (lines.value.length > limit) {
    lines.value = lines.value.slice(lines.value.length - limit);
  }
}

async function scrollToBottom() {
  if (!autoScroll.value) return;
  await nextTick();
  if (terminalRef.value) {
    terminalRef.value.scrollTop = terminalRef.value.scrollHeight;
  }
}

function connect() {
  const url = socketUrl();
  if (!url) {
    status.value = 'error';
    lines.value.push('缺少登录 token，无法连接日志 WebSocket。');
    return;
  }
  close();
  status.value = 'connecting';
  ws = new WebSocket(url);
  ws.onopen = () => {
    status.value = 'open';
    lastMessageAt.value = new Date().toLocaleTimeString();
  };
  ws.onmessage = (event) => {
    if (!paused.value) {
      lines.value.push(String(event.data));
      trimLines();
      lastMessageAt.value = new Date().toLocaleTimeString();
      scrollToBottom();
    }
  };
  ws.onerror = () => {
    status.value = 'error';
  };
  ws.onclose = () => {
    if (status.value !== 'error') status.value = 'closed';
    ws = null;
  };
}

async function loadRecentLogs(announce = false) {
  loadingHistory.value = true;
  try {
    const params = new URLSearchParams({
      limit: String(lineLimit.value || 500)
    });
    if (level.value) params.set('level', level.value);
    if (keyword.value.trim()) params.set('keyword', keyword.value.trim());
    const res = await apiGet<{ lines?: string[] }>(`/v1/logs/recent?${params.toString()}`);
    const history = res.data?.lines || [];
    lines.value = history;
    lastMessageAt.value = new Date().toLocaleTimeString();
    await scrollToBottom();
    if (announce) {
      notifyGlobal(history.length ? `已加载最近 ${history.length} 行日志` : '没有读取到匹配的历史日志', 'info');
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '读取历史日志失败';
    lines.value.push(`读取历史日志失败：${message}`);
    notifyGlobal(message, 'error');
  } finally {
    loadingHistory.value = false;
  }
}

async function reconnect() {
  lines.value = [];
  paused.value = false;
  await loadRecentLogs(false);
  connect();
}

function close() {
  if (ws) {
    ws.close();
    ws = null;
  }
  if (status.value !== 'idle') status.value = 'closed';
}

function clear() {
  lines.value = [];
  lastMessageAt.value = '';
}

function togglePause() {
  paused.value = !paused.value;
}

onMounted(async () => {
  await loadRecentLogs(false);
  connect();
});
onBeforeUnmount(close);
</script>

<template>
  <section class="wd-page">
    <div class="wd-page-title">
      <div>
        <h1>服务日志</h1>
        <p>实时查看后端运行日志，支持级别过滤、关键字检索、暂停显示和最近日志重新加载。</p>
      </div>
      <div class="wd-actions">
        <button type="button" @click="connect"><Play :size="16" />连接</button>
        <button type="button" class="ghost" @click="togglePause">
          <Pause :size="16" />{{ paused ? '继续' : '暂停' }}
        </button>
        <button type="button" class="ghost" :disabled="loadingHistory" @click="reconnect">
          <RefreshCw :size="16" :class="{ spinning: loadingHistory }" />{{ loadingHistory ? '加载中' : '重载历史' }}
        </button>
        <button type="button" class="danger" @click="clear"><Trash2 :size="16" />清空</button>
      </div>
    </div>

    <div class="wd-log-summary">
      <div class="wd-card wd-stat-card">
        <span>连接状态</span>
        <strong><span class="wd-dot" :class="statusClass"></span>{{ statusText }}</strong>
      </div>
      <div class="wd-card wd-stat-card">
        <span>当前行数</span>
        <strong>{{ lines.length }} / {{ lineLimit }}</strong>
      </div>
      <div class="wd-card wd-stat-card">
        <span>WARN / ERROR</span>
        <strong>{{ stats.warn }} / {{ stats.error }}</strong>
      </div>
      <div class="wd-card wd-stat-card">
        <span>最近更新</span>
        <strong>{{ lastMessageAt || '-' }}</strong>
      </div>
    </div>

    <div class="wd-card wd-log-card">
      <header>
        <h2><FileText :size="17" /> 实时日志</h2>
        <div class="wd-table-tools">
          <label class="wd-inline-search">
            <Search :size="15" />
            <input v-model="keyword" placeholder="搜索日志内容..." />
          </label>
          <select v-model="level" @change="loadRecentLogs(true)">
            <option value="">全部级别</option>
            <option value="INFO">INFO</option>
            <option value="WARN">WARN</option>
            <option value="ERROR">ERROR</option>
          </select>
          <select v-model.number="lineLimit" @change="loadRecentLogs(true)">
            <option :value="200">保留 200 行</option>
            <option :value="500">保留 500 行</option>
            <option :value="1000">保留 1000 行</option>
          </select>
          <label class="wd-check">
            <input v-model="autoScroll" type="checkbox" />
            自动滚动
          </label>
        </div>
      </header>
      <pre ref="terminalRef" class="wd-terminal wd-log-terminal">{{
        visibleLines.length ? visibleLines.join('\n') : '等待日志推送或调整筛选条件...'
      }}</pre>
      <footer class="wd-table-footer">
        已显示 {{ visibleLines.length }} 行 · INFO {{ stats.info }} · WARN {{ stats.warn }} · ERROR {{ stats.error }}
      </footer>
    </div>
  </section>
</template>
