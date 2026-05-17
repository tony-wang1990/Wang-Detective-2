<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bot,
  ClipboardList,
  FileText,
  Home,
  LogOut,
  Menu,
  Moon,
  ExternalLink,
  RefreshCw,
  Search,
  ServerCog,
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
let healthTimer: number | undefined;
let versionTimer: number | undefined;

const navItems = [
  { label: '主页', path: '/dashboard/home', icon: Home, match: ['/dashboard', '/dashboard/home'] },
  { label: '配置列表', path: '/dashboard/user', icon: UserRound },
  { label: '任务列表', path: '/dashboard/createTask', icon: ClipboardList },
  { label: '服务日志', path: '/dashboard/ociLog', icon: FileText },
  { label: '操作审计', path: '/dashboard/ops-audit', icon: ShieldCheck },
  { label: '系统配置', path: '/dashboard/sysCfg', icon: Settings },
  { label: 'AI聊天室', path: '/dashboard/ai-chat', icon: Bot },
  { label: '新版功能', path: '/dashboard/features', icon: ServerCog, badge: 'NEW' },
  { label: '运维终端', path: '/dashboard/ops-terminal', icon: Terminal }
];

const currentVersion = computed(() => version.value);
const healthClass = computed(() => (healthStatus.value === '正常' || healthStatus.value === 'UP' ? 'ok' : 'warn'));

function isActive(item: { path: string; match?: string[] }) {
  return item.match ? item.match.includes(route.path) : route.path === item.path;
}

function logout() {
  sessionStorage.clear();
  router.push('/login');
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
    versionStatus.value = updateAvailable.value ? '发现新版本，可点击更新' : '已是最新版本';
  } catch (error) {
    versionStatus.value = error instanceof Error ? error.message : '版本检测失败';
  }
}

async function triggerVersionUpdate() {
  if (!updateAvailable.value && !window.confirm('当前没有检测到新版本，仍然触发更新吗？')) {
    return;
  }
  updatingVersion.value = true;
  versionStatus.value = '正在触发更新...';
  try {
    const res = await apiPost<string>('/v1/system/trigger-update', {});
    versionStatus.value = res.msg || res.data || '已触发更新，watcher 将自动拉取并重启服务';
  } catch (error) {
    versionStatus.value = error instanceof Error ? error.message : '触发更新失败';
  } finally {
    updatingVersion.value = false;
  }
}

onMounted(() => {
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
});
</script>

<template>
  <div class="wd-shell">
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
          <em v-if="item.badge">{{ item.badge }}</em>
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
        <button type="button" class="wd-icon-button" aria-label="菜单">
          <Menu :size="20" />
        </button>
        <label class="wd-search">
          <Search :size="16" />
          <input placeholder="搜索资源、任务、日志等..." readonly />
          <kbd>⌘K</kbd>
        </label>
        <div class="wd-top-status">
          <span class="dot" :class="healthClass"></span>
          系统健康 <b :class="healthClass">{{ healthStatus }}</b>
        </div>
        <div class="wd-version">版本 <b>{{ currentVersion }}</b></div>
        <button
          type="button"
          class="wd-update"
          :class="{ available: updateAvailable }"
          :disabled="updatingVersion"
          :title="versionStatus"
          @click="updateAvailable ? triggerVersionUpdate() : refreshVersionInfo()"
        >
          <RefreshCw :size="16" :class="{ spinning: updatingVersion }" />
          {{ updateAvailable ? `更新 ${latestVersion || ''}` : '检查更新' }}
        </button>
        <button type="button" class="wd-theme" @click="toggleTheme">
          <Sun v-if="theme === 'dark'" :size="16" />
          <Moon v-else :size="16" />
          {{ theme === 'dark' ? '开灯' : '关灯' }}
        </button>
        <a class="wd-legacy" href="/legacy-dashboard.html">
          <ExternalLink :size="16" />
          旧版入口
        </a>
        <button type="button" class="wd-logout" @click="logout">
          <LogOut :size="16" />
          退出登录
        </button>
      </header>

      <main class="wd-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>
