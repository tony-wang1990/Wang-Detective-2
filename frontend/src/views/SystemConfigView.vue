<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  Bell,
  Bot,
  CheckCircle2,
  Database,
  KeyRound,
  LockKeyhole,
  MessageSquareText,
  RefreshCw,
  Save,
  ShieldCheck,
  Wrench
} from 'lucide-vue-next';
import { apiGet, apiPost } from '../api/http';

type DiagItem = {
  name?: string;
  key?: string;
  status?: string;
  message?: string;
  detail?: string;
};

type SystemConfig = {
  adminAccount?: string;
  dingToken: string;
  dingSecret: string;
  tgChatId: string;
  tgBotToken: string;
  enableMfa: boolean;
  mfaSecret?: string;
  mfaQrData?: string;
  enableDailyBroadcast: boolean;
  dailyBroadcastCron: string;
  enableVersionInform: boolean;
  bootBroadcastToken: string;
  enableGoogleLogin: boolean;
  googleClientId: string;
  allowedEmails: string;
  enableKeepAlive: boolean;
};

const loading = ref(false);
const saving = ref(false);
const diagnosticsLoading = ref(false);
const testingMessage = ref(false);
const checkingMfa = ref(false);
const savingCredential = ref(false);
const cfg = reactive<SystemConfig>({
  adminAccount: '',
  dingToken: '',
  dingSecret: '',
  tgChatId: '',
  tgBotToken: '',
  enableMfa: false,
  mfaSecret: '',
  mfaQrData: '',
  enableDailyBroadcast: false,
  dailyBroadcastCron: '0 0 8 * * ?',
  enableVersionInform: true,
  bootBroadcastToken: '',
  enableGoogleLogin: false,
  googleClientId: '',
  allowedEmails: '',
  enableKeepAlive: false
});
const diagnostics = ref<DiagItem[]>([]);
const raw = ref('');
const msg = ref('W-探长测试消息');
const mfaCode = ref('');
const notice = ref('');
const credentialForm = reactive({
  currentPassword: '',
  newAccount: '',
  newPassword: '',
  confirmPassword: ''
});

const diagnosticStats = computed(() => {
  const result = { ok: 0, warn: 0, error: 0 };
  diagnostics.value.forEach((item) => {
    const status = String(item.status || '').toUpperCase();
    if (status === 'OK') result.ok += 1;
    else if (status === 'ERROR') result.error += 1;
    else result.warn += 1;
  });
  return result;
});

const configReadiness = computed(() => [
  { label: 'Telegram', value: Boolean(cfg.tgBotToken && cfg.tgChatId) },
  { label: '钉钉', value: Boolean(cfg.dingToken && cfg.dingSecret) },
  { label: '每日播报', value: cfg.enableDailyBroadcast },
  { label: 'MFA', value: cfg.enableMfa },
  { label: 'Google 登录', value: cfg.enableGoogleLogin }
]);

const diagnosticNames: Record<string, string> = {
  'database-connectivity': '数据库连接',
  'database-file': '数据库文件',
  'key-directory': 'OCI 私钥目录',
  'log-file': '服务日志文件',
  'data-directory': '数据目录',
  'admin-password': '管理员密码',
  'telegram-bot': 'Telegram Bot'
};

function assignConfig(next: Partial<SystemConfig>) {
  Object.assign(cfg, {
    dingToken: next.dingToken || '',
    adminAccount: next.adminAccount || 'admin',
    dingSecret: next.dingSecret || '',
    tgChatId: next.tgChatId || '',
    tgBotToken: next.tgBotToken || '',
    enableMfa: Boolean(next.enableMfa),
    mfaSecret: next.mfaSecret || '',
    mfaQrData: next.mfaQrData || '',
    enableDailyBroadcast: Boolean(next.enableDailyBroadcast),
    dailyBroadcastCron: next.dailyBroadcastCron || '0 0 8 * * ?',
    enableVersionInform: Boolean(next.enableVersionInform),
    bootBroadcastToken: next.bootBroadcastToken || '',
    enableGoogleLogin: Boolean(next.enableGoogleLogin),
    googleClientId: next.googleClientId || '',
    allowedEmails: next.allowedEmails || '',
    enableKeepAlive: Boolean(next.enableKeepAlive)
  });
  credentialForm.newAccount = cfg.adminAccount || 'admin';
}

async function loadCfg() {
  loading.value = true;
  notice.value = '';
  try {
    const res = await apiPost<SystemConfig>('/sys/getSysCfg', {});
    assignConfig(res.data || {});
    notice.value = '系统配置已加载';
  } catch (err) {
    notice.value = err instanceof Error ? err.message : '读取配置失败';
  } finally {
    loading.value = false;
  }
}

async function saveCfg() {
  saving.value = true;
  notice.value = '';
  try {
    const payload = {
      dingToken: cfg.dingToken,
      dingSecret: cfg.dingSecret,
      tgChatId: cfg.tgChatId,
      tgBotToken: cfg.tgBotToken,
      enableMfa: cfg.enableMfa,
      enableDailyBroadcast: cfg.enableDailyBroadcast,
      dailyBroadcastCron: cfg.dailyBroadcastCron,
      enableVersionInform: cfg.enableVersionInform,
      bootBroadcastToken: cfg.bootBroadcastToken,
      enableGoogleLogin: cfg.enableGoogleLogin,
      googleClientId: cfg.googleClientId,
      allowedEmails: cfg.allowedEmails,
      enableKeepAlive: cfg.enableKeepAlive
    };
    const res = await apiPost<void>('/sys/updateSysCfg', payload);
    notice.value = res.msg || '保存成功';
    await loadCfg();
  } catch (err) {
    notice.value = err instanceof Error ? err.message : '保存失败';
  } finally {
    saving.value = false;
  }
}

async function loadDiagnostics() {
  diagnosticsLoading.value = true;
  try {
    const res = await apiGet<Record<string, unknown>>('/v1/system/diagnostics');
    raw.value = JSON.stringify(res.data || res, null, 2);
    diagnostics.value = ((res.data?.checks || []) as DiagItem[]).map((item) => ({
      ...item,
      key: item.key || item.name
    }));
  } catch (err) {
    raw.value = err instanceof Error ? err.message : '诊断读取失败';
  } finally {
    diagnosticsLoading.value = false;
  }
}

async function sendTestMessage() {
  testingMessage.value = true;
  notice.value = '';
  try {
    const res = await apiPost<void>('/sys/sendMsg', { message: msg.value || 'W-探长测试消息' });
    notice.value = res.msg || '测试消息已发送';
  } catch (err) {
    notice.value = err instanceof Error ? err.message : '测试消息发送失败';
  } finally {
    testingMessage.value = false;
  }
}

async function checkMfa() {
  checkingMfa.value = true;
  notice.value = '';
  try {
    const res = await apiPost<void>('/sys/checkMfaCode', { mfaCode: mfaCode.value });
    notice.value = res.msg || 'MFA 验证通过';
  } catch (err) {
    notice.value = err instanceof Error ? err.message : 'MFA 验证失败';
  } finally {
    checkingMfa.value = false;
  }
}

async function updateAdminCredential() {
  notice.value = '';
  if (!credentialForm.currentPassword) {
    notice.value = '请输入当前密码';
    return;
  }
  if (!credentialForm.newAccount.trim()) {
    notice.value = '请输入新登录账号';
    return;
  }
  if (!credentialForm.newPassword || credentialForm.newPassword.length < 8) {
    notice.value = '新密码至少需要 8 位';
    return;
  }
  if (credentialForm.newPassword !== credentialForm.confirmPassword) {
    notice.value = '两次输入的新密码不一致';
    return;
  }
  savingCredential.value = true;
  try {
    const res = await apiPost<void>('/sys/updateAdminCredential', {
      currentPassword: credentialForm.currentPassword,
      newAccount: credentialForm.newAccount,
      newPassword: credentialForm.newPassword,
      confirmPassword: credentialForm.confirmPassword
    });
    notice.value = res.msg || '登录账号密码已更新，请重新登录';
    credentialForm.currentPassword = '';
    credentialForm.newPassword = '';
    credentialForm.confirmPassword = '';
    window.setTimeout(() => {
      sessionStorage.clear();
      window.location.href = '/login';
    }, 900);
  } catch (err) {
    notice.value = err instanceof Error ? err.message : '更新登录账号密码失败';
  } finally {
    savingCredential.value = false;
  }
}

function statusClass(status?: string) {
  const value = String(status || '').toLowerCase();
  if (value === 'ok') return 'success';
  if (value === 'error') return 'danger';
  return 'warning';
}

function diagnosticName(item: DiagItem) {
  const key = String(item.key || item.name || '');
  return diagnosticNames[key] || item.name || item.key || '诊断项';
}

onMounted(() => {
  loadCfg();
  loadDiagnostics();
});
</script>

<template>
  <section class="wd-page">
    <div class="wd-page-title">
      <div>
        <h1>系统配置</h1>
        <p>通知通道、自动化、安全登录和系统诊断集中管理，保持新版控制台的明暗主题一致性。</p>
      </div>
      <div class="wd-actions">
        <button type="button" class="ghost" :disabled="loading" @click="loadCfg">
          <RefreshCw :size="16" :class="{ spinning: loading }" />{{ loading ? '重载中' : '重载配置' }}
        </button>
        <button type="button" :disabled="diagnosticsLoading" @click="loadDiagnostics">
          <ShieldCheck :size="16" />{{ diagnosticsLoading ? '刷新中' : '刷新诊断' }}
        </button>
        <button type="button" :disabled="saving" @click="saveCfg"><Save :size="16" />{{ saving ? '保存中' : '保存配置' }}</button>
      </div>
    </div>

    <p v-if="notice" class="wd-notice">{{ notice }}</p>

    <div class="wd-log-summary">
      <div v-for="item in configReadiness" :key="item.label" class="wd-card wd-stat-card">
        <span>{{ item.label }}</span>
        <strong><span class="wd-dot" :class="item.value ? 'success' : 'warning'"></span>{{ item.value ? '已配置' : '待配置' }}</strong>
      </div>
    </div>

    <section class="wd-split">
      <div class="wd-card wd-form-card">
        <header><h2><Bell :size="17" /> 通知通道</h2></header>
        <div class="wd-form-grid">
          <label>
            <span>Telegram Bot Token</span>
            <input v-model="cfg.tgBotToken" autocomplete="new-password" type="password" placeholder="请输入 Telegram Bot Token" />
          </label>
          <label>
            <span>Telegram 个人 ID / Chat ID</span>
            <input v-model="cfg.tgChatId" placeholder="请输入 Telegram 个人 ID" />
          </label>
          <label>
            <span>钉钉 Access Token</span>
            <input v-model="cfg.dingToken" autocomplete="new-password" type="password" placeholder="请输入钉钉 Access Token" />
          </label>
          <label>
            <span>钉钉 Secret</span>
            <input v-model="cfg.dingSecret" autocomplete="new-password" type="password" placeholder="请输入钉钉 Secret" />
          </label>
          <label class="wide">
            <span>开机播报 Token</span>
            <input v-model="cfg.bootBroadcastToken" autocomplete="new-password" type="password" placeholder="可选：用于开机播报回调" />
          </label>
        </div>
      </div>

      <div class="wd-card wd-form-card">
        <header><h2><Wrench :size="17" /> 测试工具</h2></header>
        <div class="wd-form-grid single">
          <label>
            <span>测试消息</span>
            <textarea v-model="msg" placeholder="输入要发送到 Telegram/钉钉的测试消息" />
          </label>
          <button type="button" :disabled="testingMessage" @click="sendTestMessage">
            <MessageSquareText :size="16" />{{ testingMessage ? '发送中' : '发送测试消息' }}
          </button>
          <label>
            <span>MFA 验证码</span>
            <input v-model="mfaCode" placeholder="输入 MFA 验证码" />
          </label>
          <button type="button" class="ghost" :disabled="checkingMfa" @click="checkMfa">
            <LockKeyhole :size="16" />{{ checkingMfa ? '验证中' : '验证 MFA' }}
          </button>
        </div>
      </div>
    </section>

    <section class="wd-split">
      <div class="wd-card wd-form-card">
        <header><h2><CheckCircle2 :size="17" /> 自动化与版本</h2></header>
        <div class="wd-form-grid">
          <label class="wd-switch-row">
            <input v-model="cfg.enableDailyBroadcast" type="checkbox" />
            <span>启用每日日报</span>
          </label>
          <label>
            <span>日报 Cron 表达式</span>
            <input v-model="cfg.dailyBroadcastCron" placeholder="0 0 8 * * ?" />
          </label>
          <label class="wd-switch-row">
            <input v-model="cfg.enableVersionInform" type="checkbox" />
            <span>启用版本更新通知</span>
          </label>
          <label class="wd-switch-row wide">
            <input v-model="cfg.enableKeepAlive" type="checkbox" />
            <span>启用 OCI 实例自动保活 (SSH 心跳)</span>
          </label>
        </div>
      </div>

      <div class="wd-card wd-form-card">
        <header><h2><KeyRound :size="17" /> 安全与登录</h2></header>
        <div class="wd-form-grid">
          <label class="wd-switch-row">
            <input v-model="cfg.enableMfa" type="checkbox" />
            <span>启用 MFA 登录验证</span>
          </label>
          <label>
            <span>MFA Secret</span>
            <input :value="cfg.mfaSecret || '未生成或未启用'" readonly type="password" />
          </label>
          <label class="wd-switch-row">
            <input v-model="cfg.enableGoogleLogin" type="checkbox" />
            <span>启用 Google 一键登录</span>
          </label>
          <label>
            <span>Google Client ID</span>
            <input v-model="cfg.googleClientId" placeholder="请输入 Google Client ID" />
          </label>
          <label class="wide">
            <span>允许登录邮箱</span>
            <textarea v-model="cfg.allowedEmails" placeholder="多个邮箱可用逗号或换行分隔" />
          </label>
          <label>
            <span>当前登录账号</span>
            <input :value="cfg.adminAccount || 'admin'" readonly />
          </label>
          <label>
            <span>新登录账号</span>
            <input v-model="credentialForm.newAccount" autocomplete="username" placeholder="例如 admin" />
          </label>
          <label>
            <span>当前密码</span>
            <input v-model="credentialForm.currentPassword" autocomplete="current-password" type="password" placeholder="输入当前密码确认身份" />
          </label>
          <label>
            <span>新密码</span>
            <input v-model="credentialForm.newPassword" autocomplete="new-password" type="password" placeholder="至少 8 位" />
          </label>
          <label>
            <span>确认新密码</span>
            <input v-model="credentialForm.confirmPassword" autocomplete="new-password" type="password" placeholder="再次输入新密码" />
          </label>
          <button type="button" class="ghost" :disabled="savingCredential" @click="updateAdminCredential">
            <KeyRound :size="16" />{{ savingCredential ? '更新中' : '更新登录账号密码' }}
          </button>
          <div v-if="cfg.mfaQrData" class="wd-mfa-preview">
            <img :src="cfg.mfaQrData" alt="MFA QR Code" />
            <span>扫码绑定 MFA 后再保存并测试验证码。</span>
          </div>
        </div>
      </div>
    </section>

    <section class="wd-split">
      <div class="wd-card">
        <header>
          <h2><Database :size="17" /> 系统诊断</h2>
          <span v-if="diagnosticsLoading">刷新中...</span>
          <span v-else>OK {{ diagnosticStats.ok }} · WARN {{ diagnosticStats.warn }} · ERROR {{ diagnosticStats.error }}</span>
        </header>
        <div class="wd-health-list">
          <div v-for="item in diagnostics" :key="item.key || item.name">
            <Database :size="18" />
            <b>{{ diagnosticName(item) }}</b>
            <em :class="statusClass(item.status)">{{ item.status || 'INFO' }}</em>
            <small>{{ item.message || item.detail }}</small>
          </div>
          <p v-if="!diagnostics.length" class="wd-muted-line">暂无诊断返回。</p>
        </div>
      </div>
      <div class="wd-card wd-log-card">
        <header><h2><Bot :size="17" /> 接口原始返回</h2></header>
        <pre class="wd-terminal small">{{ raw || '暂无诊断返回' }}</pre>
      </div>
    </section>
  </section>
</template>
