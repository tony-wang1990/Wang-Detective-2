<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bot,
  ClipboardList,
  DatabaseBackup,
  FileText,
  Home,
  LifeBuoy,
  LogOut,
  Menu,
  Moon,
  RefreshCw,
  ServerCog,
  ShieldAlert,
  ShieldCheck,
  Settings,
  Sun,
  Terminal,
  UserRound
} from 'lucide-vue-next';
import { useTheme } from '../composables/useTheme';
import { apiGet, apiPost, getHealth } from '../api/http';

type VersionInfo = {
  currentVersion?: string;
  latestVersion?: string;
  updateAvailable?: boolean;
  watcherActive?: boolean;
  checkedAt?: number;
};

const router = useRouter();
const route = useRoute();
const { theme, toggleTheme } = useTheme();
const host = window.location.host;
const healthStatus = ref('检查中');
const version = ref(localStorage.getItem('currentVersion') || 'main');
const latestVersion = ref(localStorage.getItem('latestVersion') || '');
const updateAvailable = ref(false);
const versionStatus = ref('');
const updatingVersion = ref(false);
const updateConfirmOpen = ref(false);
const toastMessage = ref('');
const toastKind = ref<'success' | 'error' | 'info'>('info');
const sidebarCollapsed = ref(false);
const networkActive = ref(false);
const networkCount = ref(0);
let healthTimer: number | undefined;
let versionTimer: number | undefined;
let toastTimer: number | undefined;

const navItems = [
  { label: '主页', path: '/dashboard/home', icon: Home, match: ['/dashboard', '/dashboard/home'] },
  { label: '配置列表', path: '/dashboard/user', icon: UserRound },
  { label: '任务列表', path: '/dashboard/createTask', icon: ClipboardList },
  { label: '风险看板', path: '/dashboard/risk', icon: ShieldAlert },
  { label: '备份归档', path: '/dashboard/backups', icon: DatabaseBackup },
  { label: '救援中心', path: '/dashboard/rescue', icon: LifeBuoy },
  { label: '功能中心', path: '/dashboard/features', icon: ServerCog },
  { label: '运维终端', path: '/dashboard/ops-terminal', icon: Terminal },
  { label: 'AI聊天室', path: '/dashboard/ai-chat', icon: Bot },
  { label: '服务日志', path: '/dashboard/ociLog', icon: FileText },
  { label: '操作审计', path: '/dashboard/ops-audit', icon: ShieldCheck },
  { label: '系统配置', path: '/dashboard/sysCfg', icon: Settings }
];

const currentVersion = computed(() => version.value);
const healthClass = computed(() => (healthStatus.value === '正常' || healthStatus.value === 'UP' ? 'ok' : 'warn'));

function isActive(item: { path: string; match?: string[] }) {
  return item.match ? item.match.includes(route.path) : route.path === item.path;
}

function logout() {
  sessionStorage.clear();
  localStorage.removeItem('currentVersion');
  localStorage.removeItem('latestVersion');
  router.push('/login');
}

function notify(message: string, kind: 'success' | 'error' | 'info' = 'info') {
  toastMessage.value = message;
  toastKind.value = kind;
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
  toastTimer = window.setTimeout(() => {
    toastMessage.value = '';
  }, 5200);
}

function handleToast(event: Event) {
  const detail = (event as CustomEvent<{ message?: string; kind?: 'success' | 'error' | 'info' }>).detail || {};
  if (detail.message) {
    notify(detail.message, detail.kind || 'info');
  }
}

function handleNetwork(event: Event) {
  const detail = (event as CustomEvent<{ active?: boolean; count?: number }>).detail || {};
  networkActive.value = Boolean(detail.active);
  networkCount.value = Number(detail.count || 0);
}

async function refreshTopStatus() {
  try {
    const health = await getHealth();
    healthStatus.value = health.status === 'UP' ? '正常' : health.status || '未知';
    if (health.version) {
      version.value = health.version;
      localStorage.setItem('currentVersion', health.version);
    }
  } catch {
    healthStatus.value = '异常';
  }
}

async function refreshVersionInfo() {
  try {
    const res = await apiGet<VersionInfo>('/v1/system/version-info');
    const info = res.data || {};
    if (info.currentVersion) {
      version.value = info.currentVersion;
      localStorage.setItem('currentVersion', info.currentVersion);
    }
    if (info.latestVersion) {
      latestVersion.value = info.latestVersion;
      localStorage.setItem('latestVersion', info.latestVersion);
    }
    updateAvailable.value = Boolean(info.updateAvailable ?? (
      info.latestVersion && info.currentVersion && info.latestVersion !== info.currentVersion
    ));
    versionStatus.value = info.watcherActive === false
      ? '自动更新 watcher 未运行，请重新执行安装脚本启用'
      : updateAvailable.value
        ? '发现新版本，可点击更新'
        : '已是最新版本';
  } catch (error) {
    versionStatus.value = error instanceof Error ? error.message : '版本检测失败';
  }
}

function requestVersionUpdate() {
  if (updateAvailable.value) {
    updateConfirmOpen.value = true;
    return;
  }
  refreshVersionInfo();
}

async function triggerVersionUpdate() {
  if (!updateAvailable.value) {
    updateConfirmOpen.value = true;
    return;
  }
  updatingVersion.value = true;
  updateConfirmOpen.value = false;
  versionStatus.value = '正在触发更新...';
  try {
    const res = await apiPost<string>('/v1/system/trigger-update', {});
    const message = res.msg || res.data || '已触发更新，watcher 将自动拉取并重启服务';
    versionStatus.value = message;
    notify(message, 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '触发更新失败';
    versionStatus.value = message;
    notify(message, 'error');
  } finally {
    updatingVersion.value = false;
  }
}

async function forceVersionUpdate() {
  updatingVersion.value = true;
  updateConfirmOpen.value = false;
  versionStatus.value = '正在触发更新...';
  try {
    const res = await apiPost<string>('/v1/system/trigger-update', {});
    const message = res.msg || res.data || '已触发更新，watcher 将自动拉取并重启服务';
    versionStatus.value = message;
    notify(message, 'success');
  } catch (error) {
    const message = error instanceof Error ? error.message : '触发更新失败';
    versionStatus.value = message;
    notify(message, 'error');
  } finally {
    updatingVersion.value = false;
  }
}

function confirmVersionUpdate() {
  if (updateAvailable.value) {
    triggerVersionUpdate();
  } else {
    forceVersionUpdate();
  }
}

onMounted(() => {
  window.addEventListener('wd:toast', handleToast);
  window.addEventListener('wd:network', handleNetwork);
  refreshTopStatus();
  refreshVersionInfo();
  healthTimer = window.setInterval(refreshTopStatus, 60000);
  versionTimer = window.setInterval(refreshVersionInfo, 300000);
});

onBeforeUnmount(() => {
  if (healthTimer) {
    window.clearInterval(healthTimer);
  }
  if (versionTimer) {
    window.clearInterval(versionTimer);
  }
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
  window.removeEventListener('wd:toast', handleToast);
  window.removeEventListener('wd:network', handleNetwork);
});
</script>

<template>
  <div class="wd-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <aside class="wd-sidebar">
      <div class="wd-brand">
        <div class="wd-logo">W</div>
        <strong>W-探长</strong>
      </div>

      <nav class="wd-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          :class="{ active: isActive(item) }"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" :size="19" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="wd-sidebar-card">
        <span>API 网关地址</span>
        <strong>{{ host }}</strong>
        <small>生产环境 · W-探长</small>
      </div>
    </aside>

    <section class="wd-main">
      <header class="wd-topbar">
        <button
          type="button"
          class="wd-icon-button"
          :aria-label="sidebarCollapsed ? '展开菜单' : '收起菜单'"
          :title="sidebarCollapsed ? '展开菜单' : '收起菜单'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <Menu :size="20" />
        </button>
        <div class="wd-top-status">
          <span class="dot" :class="healthClass"></span>
          系统健康 <b :class="healthClass">{{ healthStatus }}</b>
        </div>
        <div v-if="networkActive" class="wd-network-indicator">
          <RefreshCw :size="14" class="spinning" />
          请求中 {{ networkCount }}
        </div>
        <div class="wd-version">版本 <b>{{ currentVersion }}</b></div>
        <button
          type="button"
          class="wd-update"
          :class="{ available: updateAvailable }"
          :disabled="updatingVersion"
          :title="versionStatus"
          @click="requestVersionUpdate"
        >
          <RefreshCw :size="16" :class="{ spinning: updatingVersion }" />
          {{ updateAvailable ? `更新 ${latestVersion || ''}` : '检查更新' }}
        </button>
        <button type="button" class="wd-theme" @click="toggleTheme">
          <Sun v-if="theme === 'dark'" :size="16" />
          <Moon v-else :size="16" />
          {{ theme === 'dark' ? '开灯' : '关灯' }}
        </button>

        <button type="button" class="wd-logout" @click="logout">
          <LogOut :size="16" />
          退出登录
        </button>
      </header>

      <nav class="wd-mobile-nav" aria-label="移动端导航">
        <button
          v-for="item in navItems"
          :key="`mobile-${item.path}`"
          type="button"
          :class="{ active: isActive(item) }"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <main class="wd-content">
        <RouterView />
      </main>
    </section>

    <div v-if="toastMessage" class="wd-toast" :class="toastKind">
      {{ toastMessage }}
    </div>

    <div v-if="updateConfirmOpen" class="wd-dialog-backdrop" @click.self="updateConfirmOpen = false">
      <form class="wd-dialog" :class="{ danger: !updateAvailable }" @submit.prevent="confirmVersionUpdate">
        <header>
          <div>
            <span>{{ updateAvailable ? '版本更新确认' : '强制更新确认' }}</span>
            <h3>{{ updateAvailable ? '更新到最新版本' : '当前未检测到新版本' }}</h3>
          </div>
          <button type="button" class="ghost" @click="updateConfirmOpen = false">关闭</button>
        </header>
        <p>
          {{
            updateAvailable
              ? `将触发 watcher 拉取 ${latestVersion || '最新镜像'} 并重启服务，通常需要 1-3 分钟。`
              : '未检测到新版本，仍可强制触发 watcher 重新拉取镜像并重启服务。'
          }}
        </p>
        <div class="wd-dialog-target">
          <span>当前版本</span>
          <strong>{{ currentVersion }}</strong>
        </div>
        <footer>
          <button type="button" class="ghost" @click="updateConfirmOpen = false">取消</button>
          <button type="submit" :class="{ danger: !updateAvailable }" :disabled="updatingVersion">
            {{ updatingVersion ? '触发中...' : updateAvailable ? '确认更新' : '强制更新' }}
          </button>
        </footer>
      </form>
    </div>
  </div>
</template>
