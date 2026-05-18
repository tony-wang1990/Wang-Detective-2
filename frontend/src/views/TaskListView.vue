<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ClipboardList, ExternalLink, RefreshCw, Search, Square, TimerReset } from 'lucide-vue-next';
import { apiPost, notifyGlobal, type PageResult } from '../api/http';

type Row = {
  id?: string;
  username?: string;
  region?: string;
  ocpus?: string;
  memory?: string;
  disk?: number;
  architecture?: string;
  interval?: number;
  createNumbers?: number;
  operationSystem?: string;
  createTime?: string;
  counts?: string;
  [key: string]: unknown;
};

type ConfirmDialog = {
  title: string;
  description: string;
  target?: string;
  ids: string[];
  actionLabel: string;
};

const loading = ref(false);
const stopping = ref(false);
const keyword = ref('');
const architecture = ref('');
const rows = ref<Row[]>([]);
const total = ref(0);
const error = ref('');
const notice = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const selectedIds = ref<string[]>([]);
const selectedDetail = ref<Row | null>(null);
const confirmDialog = ref<ConfirmDialog | null>(null);

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const selectedCount = computed(() => selectedIds.value.length);

const columns = [
  { key: 'username', label: '配置名称' },
  { key: 'region', label: '区域' },
  { key: 'shape', label: '规格' },
  { key: 'operationSystem', label: '系统' },
  { key: 'architecture', label: '架构' },
  { key: 'interval', label: '间隔' },
  { key: 'createNumbers', label: '数量' },
  { key: 'counts', label: '尝试次数' },
  { key: 'createTime', label: '创建时间' }
];

function rowId(row: Row) {
  return String(row.id || '');
}

function taskName(row: Row) {
  return String(row.username || '-');
}

function cell(row: Row, key: string) {
  if (key === 'username') return taskName(row);
  if (key === 'shape') {
    const cpu = row.ocpus || '-';
    const memory = row.memory || '-';
    const disk = row.disk ?? '-';
    return `${cpu}C / ${memory}G / ${disk}G`;
  }
  if (key === 'interval') return row.interval ? `${row.interval} 秒` : '-';
  const value = row[key];
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

function architectureClass(row: Row) {
  const value = String(row.architecture || '').toUpperCase();
  if (value === 'ARM') return 'success';
  if (value.includes('AMD')) return 'info';
  return 'muted';
}

function toggleRow(row: Row) {
  const id = rowId(row);
  if (!id) return;
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id];
}

function toggleAll(checked: boolean) {
  selectedIds.value = checked ? rows.value.map(rowId).filter(Boolean) : [];
}

function resetPageAndLoad() {
  currentPage.value = 1;
  load();
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const res = await apiPost<PageResult<Row>>('/oci/createTaskPage', {
      keyword: keyword.value,
      architecture: architecture.value,
      currentPage: currentPage.value,
      pageSize: pageSize.value
    });
    rows.value = res.data?.records || [];
    total.value = Number(res.data?.total || rows.value.length || 0);
    selectedIds.value = selectedIds.value.filter((id) => rows.value.some((row) => rowId(row) === id));
    notice.value = `已刷新：${new Date().toLocaleTimeString()}`;
  } catch (err) {
    error.value = err instanceof Error ? err.message : '任务列表读取失败';
  } finally {
    loading.value = false;
  }
}

async function stopTask(row: Row) {
  const id = rowId(row);
  if (!id) return;
  confirmDialog.value = {
    title: '停止开机任务',
    description: '停止后该配置的抢机任务会被取消，正在执行中的任务可能需要等待后端完成当前轮询。',
    target: taskName(row),
    ids: [id],
    actionLabel: '确认停止'
  };
}

async function submitStopDialog() {
  const current = confirmDialog.value;
  if (!current || current.ids.length === 0) return;
  stopping.value = true;
  try {
    await apiPost('/oci/stopCreateBatch', { idList: current.ids });
    notice.value = current.ids.length > 1 ? '批量停止任务已提交' : '停止任务已提交';
    notifyGlobal(notice.value, 'success');
    selectedIds.value = selectedIds.value.filter((item) => !current.ids.includes(item));
    confirmDialog.value = null;
    await load();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '停止任务失败';
    notifyGlobal(error.value, 'error');
  } finally {
    stopping.value = false;
  }
}

async function stopSelected() {
  if (!selectedIds.value.length) return;
  confirmDialog.value = {
    title: '批量停止开机任务',
    description: '将停止当前选中的开机任务，请确认这些任务不再需要继续抢机。',
    target: `${selectedIds.value.length} 个任务`,
    ids: [...selectedIds.value],
    actionLabel: '批量停止'
  };
}

function closeConfirmDialog() {
  if (stopping.value) return;
  confirmDialog.value = null;
}

function previousPage() {
  if (currentPage.value > 1) {
    currentPage.value -= 1;
    load();
  }
}

function nextPage() {
  if (currentPage.value < totalPages.value) {
    currentPage.value += 1;
    load();
  }
}

onMounted(load);
</script>

<template>
  <section class="wd-page">
    <div class="wd-page-title">
      <div>
        <h1>任务列表</h1>
        <p>集中查看和停止开机任务，支持关键字、架构筛选、分页、批量操作和任务详情预览。</p>
      </div>
      <div class="wd-actions">
        <button type="button" @click="load"><RefreshCw :size="16" />刷新</button>
        <button type="button" class="danger" :disabled="selectedCount === 0" @click="stopSelected">
          <Square :size="16" />批量停止 {{ selectedCount || '' }}
        </button>
      </div>
    </div>

    <div class="wd-card wd-table-card">
      <header>
        <h2><ClipboardList :size="17" /> 开机任务</h2>
        <div class="wd-table-tools">
          <label class="wd-inline-search">
            <Search :size="15" />
            <input v-model="keyword" placeholder="搜索配置名称、区域、架构..." @keyup.enter="resetPageAndLoad" />
            <button type="button" @click="resetPageAndLoad">查询</button>
          </label>
          <select v-model="architecture" @change="resetPageAndLoad">
            <option value="">全部架构</option>
            <option value="ARM">ARM</option>
            <option value="AMD">AMD</option>
            <option value="AMD_E5">AMD_E5</option>
          </select>
        </div>
      </header>
      <p v-if="error" class="wd-error-line">{{ error }}</p>
      <p v-else-if="notice" class="wd-muted-line">{{ notice }}</p>

      <table class="wd-table">
        <thead>
          <tr>
            <th>
              <input
                type="checkbox"
                :checked="rows.length > 0 && selectedCount === rows.length"
                @change="toggleAll(($event.target as HTMLInputElement).checked)"
              />
            </th>
            <th v-for="column in columns" :key="column.key">{{ column.label }}</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="columns.length + 3">加载中...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td :colspan="columns.length + 3">暂无开机任务</td>
          </tr>
          <tr
            v-for="(row, index) in rows"
            v-else
            :key="String(row.id || index)"
            :class="{ selected: selectedIds.includes(rowId(row)) }"
          >
            <td><input type="checkbox" :checked="selectedIds.includes(rowId(row))" @change="toggleRow(row)" /></td>
            <td v-for="column in columns" :key="column.key">
              <span v-if="column.key === 'architecture'" class="wd-badge" :class="architectureClass(row)">
                {{ cell(row, column.key) }}
              </span>
              <span v-else-if="column.key === 'counts'" class="wd-badge warning">{{ cell(row, column.key) }}</span>
              <span v-else>{{ cell(row, column.key) }}</span>
            </td>
            <td><span class="wd-badge success">运行中</span></td>
            <td>
              <div class="wd-row-actions">
                <button type="button" @click="selectedDetail = row"><ExternalLink :size="14" />详情</button>
                <button type="button" class="danger-soft" @click="stopTask(row)"><Square :size="14" />停止</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <footer class="wd-table-footer wd-pager">
        <span>共 {{ total }} 条 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
        <select v-model.number="pageSize" @change="resetPageAndLoad">
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
        <button type="button" :disabled="currentPage <= 1" @click="previousPage">上一页</button>
        <button type="button" :disabled="currentPage >= totalPages" @click="nextPage">下一页</button>
      </footer>
    </div>

    <div v-if="selectedDetail" class="wd-card wd-detail-card">
      <header>
        <h2><TimerReset :size="17" /> 任务详情</h2>
        <button type="button" @click="selectedDetail = null">关闭</button>
      </header>
      <pre class="wd-terminal small">{{ JSON.stringify(selectedDetail, null, 2) }}</pre>
    </div>

    <div v-if="confirmDialog" class="wd-dialog-backdrop" @click.self="closeConfirmDialog">
      <form class="wd-dialog danger" @submit.prevent="submitStopDialog">
        <header>
          <div>
            <span>任务操作确认</span>
            <h3>{{ confirmDialog.title }}</h3>
          </div>
          <button type="button" class="ghost" @click="closeConfirmDialog">关闭</button>
        </header>
        <p>{{ confirmDialog.description }}</p>
        <div v-if="confirmDialog.target" class="wd-dialog-target">
          <span>目标</span>
          <strong>{{ confirmDialog.target }}</strong>
        </div>
        <footer>
          <button type="button" class="ghost" @click="closeConfirmDialog">取消</button>
          <button type="submit" class="danger" :disabled="stopping">
            {{ stopping ? '提交中...' : confirmDialog.actionLabel }}
          </button>
        </footer>
      </form>
    </div>
  </section>
</template>
