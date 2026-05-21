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
const form = reactive({
  username: 'admin',
  password: '',
  mfaCode: ''
});

async function loadMfaState() {
  try {
    const result = await apiPost<boolean>('/sys/getEnableMfa', {});
    mfaRequired.value = Boolean(result.data);
  } catch {
    mfaRequired.value = false;
  }
}

async function submit() {
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
          <p>OCI Operations Console</p>
        </div>
      </div>
      <div class="wd-login-signal">
        <strong>OCI</strong>
        <span>Compute · Network · Logs · Ops</span>
      </div>
      <ul>
        <li>资源、任务、日志集中管理</li>
        <li>系统诊断与运维入口统一呈现</li>
        <li>深色 / 浅色主题自然切换</li>
      </ul>
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
            <input v-model="form.username" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" autocomplete="current-password" />
          </label>
          <label v-if="mfaRequired">
            <span>MFA 验证码</span>
            <input v-model="form.mfaCode" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位动态验证码" />
          </label>
          <p v-if="error" class="wd-error">{{ error }}</p>
          <button type="submit" :disabled="loading">
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
