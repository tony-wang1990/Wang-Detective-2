<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { apiPost, type LoginResponse } from '../api/http';
import { useTheme } from '../composables/useTheme';

const router = useRouter();
const { theme, toggleTheme } = useTheme();
const loading = ref(false);
const error = ref('');
const mfaRequired = ref(false);
const mfaStateReady = ref(false);
const mfaStateLoading = ref(false);
const form = reactive({
  username: '',
  password: '',
  mfaCode: ''
});

async function loadMfaState() {
  mfaStateLoading.value = true;
  try {
    const result = await apiPost<boolean>('/sys/getEnableMfa', {});
    mfaRequired.value = Boolean(result.data);
    mfaStateReady.value = true;
  } catch (err) {
    mfaStateReady.value = false;
    error.value = err instanceof Error ? `无法读取 MFA 状态：${err.message}` : '无法读取 MFA 状态，请稍后重试';
  } finally {
    mfaStateLoading.value = false;
  }
}

async function submit() {
  if (!mfaStateReady.value) {
    error.value = 'MFA 状态尚未确认，请等待页面初始化完成后再登录';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const result = await apiPost<LoginResponse>('/sys/login', {
      account: form.username,
      password: form.password,
      mfaCode: mfaRequired.value ? form.mfaCode : undefined
    });
    if (!result.success || !result.data?.token) {
      throw new Error(result.msg || '登录失败');
    }
    sessionStorage.setItem('token', result.data.token);
    if (result.data.currentVersion) {
      localStorage.setItem('currentVersion', result.data.currentVersion);
    }
    if (result.data.latestVersion) {
      localStorage.setItem('latestVersion', result.data.latestVersion);
    }
    await router.replace('/dashboard/home');
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadMfaState);
</script>

<template>
  <main class="wd-login">
    <section class="wd-login-brand">
      <div class="wd-login-mark">
        <div class="wd-login-logo xl">W</div>
        <div>
          <span>WANG DETECTIVE</span>
          <h1>W-探长</h1>
          <p>OCI Operations Command Center</p>
        </div>
      </div>
      <div class="wd-login-hero-copy">
        <strong>把 OCI 资源、任务、日志、SSH、备份和救援能力收进一个控制台。</strong>
        <p>面向低配 VPS 和多区域 OCI 管理场景，强调真实操作、可审计、可回滚。</p>
      </div>
      <div class="wd-login-feature-grid">
        <article>
          <span>Compute</span>
          <strong>实例与任务</strong>
          <p>开机任务、实例状态、电源动作、引导卷和网络入口集中管理。</p>
        </article>
        <article>
          <span>Ops</span>
          <strong>终端与日志</strong>
          <p>Web SSH/SFTP、服务日志、操作审计和系统诊断统一查看。</p>
        </article>
        <article>
          <span>Safety</span>
          <strong>备份与救援</strong>
          <p>本地备份、Object Storage 归档、恢复计划和救援中心联动。</p>
        </article>
      </div>
    </section>

    <section class="wd-login-panel">
      <div class="wd-login-card">
        <div class="wd-login-card-title">
          <div class="wd-logo">W</div>
          <div>
            <h2>登录控制台</h2>
            <span>OCI 资源与运维管理</span>
          </div>
        </div>
        <form @submit.prevent="submit">
          <label>
            <span>账号</span>
            <input v-model="form.username" autocomplete="username" placeholder="请输入账号" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password" />
          </label>
          <label v-if="mfaRequired">
            <span>MFA 验证码</span>
            <input v-model="form.mfaCode" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位动态验证码" />
          </label>
          <p v-if="mfaStateLoading" class="wd-login-hint">正在确认安全登录状态...</p>
          <p v-if="loading" class="wd-login-hint">正在连接控制台服务，超过 20 秒无响应时请优先检查容器健康和反向代理源站。</p>
          <p v-if="error" class="wd-error">{{ error }}</p>
          <button type="submit" :disabled="loading || !mfaStateReady">
            {{ loading ? '登录中...' : '登录控制台' }}
          </button>
        </form>
      </div>
      <footer>
        <span>© 2026 Tony Wang</span>
        <button type="button" @click="toggleTheme">{{ theme === 'dark' ? '开灯' : '关灯' }}</button>
      </footer>
    </section>
  </main>
</template>
