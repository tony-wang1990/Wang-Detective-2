<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  ArrowLeft,
  Download,
  ExternalLink,
  Grid3X3,
  Home,
  KeyRound,
  RefreshCw,
  ShieldCheck,
  Upload
} from 'lucide-vue-next';
import { apiDownload, apiForm, apiGet, apiPostLong, notifyGlobal } from '../api/http';
import { filenameFromDisposition, openExternalUrl, saveBlob } from '../runtime/fileTransfer';

type FeatureButton = {
  text: string;
  callbackData?: string;
  url?: string;
};

type FeatureAttachment = {
  token: string;
  fileName: string;
  contentType: string;
  size: number;
  url: string;
  expiresAt: number;
};

type FeatureResponse = {
  title: string;
  text: string;
  parseMode: string;
  buttons: FeatureButton[][];
  notices: string[];
  attachments: FeatureAttachment[];
  inputPrompt?: {
    action: string;
    type: 'url' | 'password' | 'file';
    label: string;
    placeholder?: string;
    accept?: string;
    minLength: number;
    confirmText?: string;
  };
  clientRoute?: string;
  closed: boolean;
  registeredHandlerCount: number;
};

const SESSION_KEY = 'wd:featureSessionId';
const router = useRouter();
const loading = ref(false);
const downloading = ref('');
const error = ref('');
const screen = ref<FeatureResponse>();
const history = ref<FeatureResponse[]>([]);
const inputValue = ref('');
const restoreFile = ref<File>();
const restoreConfirmed = ref(false);

function featureSessionId() {
  let value = localStorage.getItem(SESSION_KEY);
  if (!value) {
    value = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(SESSION_KEY, value);
  }
  return value;
}

function decodeHtml(value: string) {
  const doc = new DOMParser().parseFromString(value, 'text/html');
  return doc.body.textContent || '';
}

function displayText(value: string, parseMode: string) {
  if (!value) return '暂无返回内容';
  if (parseMode?.toLowerCase() === 'html') {
    return decodeHtml(value.replace(/<br\s*\/?>/gi, '\n'));
  }
  return value
    .replace(/\\([_*\[\]()~`>#+\-=|{}.!\\])/g, '$1')
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/(?<!\*)\*([^*\n]+)\*/g, '$1');
}

const renderedText = computed(() => displayText(screen.value?.text || '', screen.value?.parseMode || 'plain'));

function formatSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '未知大小';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function resetInput() {
  inputValue.value = '';
  restoreFile.value = undefined;
  restoreConfirmed.value = false;
}

function applyScreen(next: FeatureResponse, pushHistory: boolean) {
  if (pushHistory && screen.value) history.value.push(screen.value);
  screen.value = next;
  resetInput();
}

function showNotices(response: FeatureResponse) {
  for (const notice of response.notices || []) {
    notifyGlobal(displayText(notice, 'MarkdownV2'), 'info');
  }
}

async function loadMenu(pushHistory = false) {
  loading.value = true;
  error.value = '';
  try {
    const response = await apiGet<FeatureResponse>('/v1/client-features/menu');
    applyScreen(response.data, pushHistory);
  } catch (err) {
    error.value = err instanceof Error ? err.message : '完整功能菜单读取失败';
  } finally {
    loading.value = false;
  }
}

async function invoke(button: FeatureButton) {
  if (button.url) {
    await openExternalUrl(button.url);
    return;
  }
  if (!button.callbackData || loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    const response = await apiPostLong<FeatureResponse>('/v1/client-features/callback', {
      callbackData: button.callbackData,
      sessionId: featureSessionId()
    });
    applyScreen(response.data, true);
    showNotices(response.data);
    if (response.data.clientRoute) {
      notifyGlobal('已进入对应的客户端操作页面', 'info');
      await router.push(response.data.clientRoute);
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '功能执行失败';
  } finally {
    loading.value = false;
  }
}

function goBack() {
  const previous = history.value.pop();
  if (previous) {
    screen.value = previous;
    resetInput();
  }
}

function selectRestoreFile(event: Event) {
  const input = event.target as HTMLInputElement;
  restoreFile.value = input.files?.[0];
}

async function submitPrompt() {
  const prompt = screen.value?.inputPrompt;
  if (!prompt || loading.value) return;
  error.value = '';

  if (prompt.type !== 'file' && inputValue.value.trim().length < prompt.minLength) {
    error.value = `${prompt.label}至少需要 ${prompt.minLength} 个字符`;
    return;
  }
  if (prompt.type === 'file' && !restoreFile.value) {
    error.value = '请选择备份 ZIP 文件';
    return;
  }
  if (prompt.confirmText && !restoreConfirmed.value) {
    error.value = '请先确认恢复操作会覆盖当前数据';
    return;
  }

  loading.value = true;
  try {
    let response;
    if (prompt.type === 'file') {
      const form = new FormData();
      form.append('sessionId', featureSessionId());
      form.append('file', restoreFile.value as File);
      form.append('password', inputValue.value);
      response = await apiForm<FeatureResponse>('/v1/client-features/restore', form);
    } else {
      response = await apiPostLong<FeatureResponse>('/v1/client-features/input', {
        callbackData: prompt.action,
        sessionId: featureSessionId(),
        value: inputValue.value
      });
    }
    applyScreen(response.data, true);
    showNotices(response.data);
    notifyGlobal(prompt.type === 'file' ? '数据恢复成功' : '操作完成', 'success');
  } catch (err) {
    error.value = err instanceof Error ? err.message : '输入操作失败';
  } finally {
    loading.value = false;
  }
}

async function downloadAttachment(attachment: FeatureAttachment) {
  if (downloading.value) return;
  downloading.value = attachment.token;
  try {
    const result = await apiDownload(`/v1/client-features/attachments/${attachment.token}`);
    const name = filenameFromDisposition(result.filename, attachment.fileName);
    await saveBlob(result.blob, name);
    notifyGlobal('文件已交给系统保存或分享', 'success');
  } catch (err) {
    notifyGlobal(err instanceof Error ? err.message : '附件下载失败', 'error');
  } finally {
    downloading.value = '';
  }
}

onMounted(() => loadMenu());
</script>

<template>
  <section class="wd-page wd-full-features">
    <header class="wd-page-title">
      <div>
        <h1>全部功能</h1>
        <p>项目原生功能统一入口，Web、Windows 与 Android 共用同一套服务和数据。</p>
      </div>
      <div class="wd-actions compact">
        <button type="button" class="ghost" :disabled="!history.length || loading" @click="goBack">
          <ArrowLeft :size="16" />返回
        </button>
        <button type="button" class="ghost" :disabled="loading" @click="loadMenu(true)">
          <Home :size="16" />主菜单
        </button>
        <button type="button" :disabled="loading" @click="loadMenu(false)">
          <RefreshCw :size="16" :class="{ spinning: loading }" />刷新
        </button>
      </div>
    </header>

    <div v-if="error" class="wd-notice danger">{{ error }}</div>

    <div class="feature-status-band">
      <span><ShieldCheck :size="16" />管理员鉴权</span>
      <span><Grid3X3 :size="16" />{{ screen?.registeredHandlerCount || 0 }} 个处理器已接入</span>
      <span>会话状态已同步</span>
    </div>

    <section class="feature-workspace" :aria-busy="loading">
      <header>
        <div>
          <span class="feature-eyebrow">当前功能</span>
          <h2>{{ screen?.title || '正在加载' }}</h2>
        </div>
        <RefreshCw v-if="loading" :size="18" class="spinning" />
      </header>

      <pre class="feature-output">{{ renderedText }}</pre>

      <div v-if="screen?.attachments?.length" class="feature-attachments">
        <button
          v-for="attachment in screen.attachments"
          :key="attachment.token"
          type="button"
          :disabled="Boolean(downloading)"
          @click="downloadAttachment(attachment)"
        >
          <Download :size="17" />
          <span><strong>{{ attachment.fileName }}</strong><small>{{ formatSize(attachment.size) }}</small></span>
          <RefreshCw v-if="downloading === attachment.token" :size="15" class="spinning" />
        </button>
      </div>

      <form v-if="screen?.inputPrompt" class="feature-input" @submit.prevent="submitPrompt">
        <label v-if="screen.inputPrompt.type === 'file'" class="feature-file-picker">
          <Upload :size="18" />
          <span>
            <strong>{{ restoreFile?.name || screen.inputPrompt.label }}</strong>
            <small>{{ restoreFile ? formatSize(restoreFile.size) : '最大 50 MB，仅支持 ZIP' }}</small>
          </span>
          <input
            type="file"
            :accept="screen.inputPrompt.accept"
            :disabled="loading"
            required
            @change="selectRestoreFile"
          />
        </label>
        <label class="feature-text-input">
          <span>{{ screen.inputPrompt.type === 'file' ? '恢复密码（未加密可留空）' : screen.inputPrompt.label }}</span>
          <div>
            <KeyRound v-if="screen.inputPrompt.type === 'password' || screen.inputPrompt.type === 'file'" :size="17" />
            <input
              v-model="inputValue"
              :type="screen.inputPrompt.type === 'url' ? 'url' : 'password'"
              :placeholder="screen.inputPrompt.placeholder"
              :minlength="screen.inputPrompt.type === 'file' ? undefined : screen.inputPrompt.minLength"
              :required="screen.inputPrompt.type !== 'file'"
              :disabled="loading"
              :autocomplete="screen.inputPrompt.type === 'url' ? 'url' : 'new-password'"
            />
          </div>
        </label>
        <label v-if="screen.inputPrompt.confirmText" class="feature-confirm">
          <input v-model="restoreConfirmed" type="checkbox" :disabled="loading" />
          <span>{{ screen.inputPrompt.confirmText }}</span>
        </label>
        <button type="submit" :class="{ danger: screen.inputPrompt.type === 'file' }" :disabled="loading">
          <RefreshCw v-if="loading" :size="16" class="spinning" />
          <Upload v-else-if="screen.inputPrompt.type === 'file'" :size="16" />
          <KeyRound v-else :size="16" />
          {{ screen.inputPrompt.type === 'file' ? '确认恢复' : '提交' }}
        </button>
      </form>

      <div v-if="screen?.buttons?.length" class="feature-keyboard">
        <div v-for="(row, rowIndex) in screen.buttons" :key="rowIndex" class="feature-keyboard-row">
          <button
            v-for="button in row"
            :key="button.callbackData || button.url || button.text"
            type="button"
            :class="{ external: button.url, danger: /确认|删除|终止|关闭|禁用/.test(button.text) }"
            :disabled="loading"
            @click="invoke(button)"
          >
            <ExternalLink v-if="button.url" :size="15" />
            {{ displayText(button.text, 'plain') }}
          </button>
        </div>
      </div>

      <p v-else-if="!loading && !screen?.inputPrompt" class="wd-empty">当前结果没有后续操作，可返回主菜单继续。</p>
    </section>
  </section>
</template>

<style scoped>
.wd-full-features { gap: 16px; }
.feature-status-band { display: flex; flex-wrap: wrap; gap: 18px; padding: 11px 14px; border: 1px solid var(--wd-line); background: var(--wd-surface); color: var(--wd-muted); font-size: 13px; }
.feature-status-band span { display: inline-flex; align-items: center; gap: 6px; }
.feature-status-band span:first-child { color: var(--wd-accent-2); }
.feature-workspace { min-height: 520px; border: 1px solid var(--wd-line); background: var(--wd-surface); }
.feature-workspace > header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px; border-bottom: 1px solid var(--wd-line); }
.feature-workspace h2 { margin: 3px 0 0; font-size: 18px; letter-spacing: 0; }
.feature-eyebrow { color: var(--wd-muted); font-size: 12px; }
.feature-output { min-height: 210px; max-height: 52vh; overflow: auto; margin: 0; padding: 20px; border: 0; background: color-mix(in srgb, var(--wd-surface) 94%, var(--wd-accent) 6%); color: var(--wd-text); font: 14px/1.75 ui-monospace, SFMono-Regular, Consolas, monospace; white-space: pre-wrap; word-break: break-word; }
.feature-keyboard { display: grid; gap: 9px; padding: 18px 20px 22px; border-top: 1px solid var(--wd-line); }
.feature-keyboard-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); gap: 9px; }
.feature-keyboard button { min-height: 42px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; padding: 9px 12px; border: 1px solid var(--wd-line); background: var(--wd-surface-2); color: var(--wd-text); font-weight: 650; cursor: pointer; }
.feature-keyboard button:hover { border-color: var(--wd-accent); color: var(--wd-accent); }
.feature-keyboard button.external { color: var(--wd-accent); }
.feature-keyboard button.danger { border-color: color-mix(in srgb, var(--wd-danger) 42%, var(--wd-line)); color: var(--wd-danger); }
.feature-keyboard button:disabled { opacity: .55; cursor: wait; }
.feature-attachments { display: grid; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--wd-line); }
.feature-attachments button { display: flex; align-items: center; gap: 10px; width: 100%; padding: 11px 12px; border: 1px solid var(--wd-line); background: var(--wd-surface-2); color: var(--wd-text); text-align: left; cursor: pointer; }
.feature-attachments button span { display: grid; gap: 2px; flex: 1; min-width: 0; }
.feature-attachments strong { overflow: hidden; text-overflow: ellipsis; }
.feature-attachments small { color: var(--wd-muted); }
.feature-input { display: grid; gap: 13px; padding: 18px 20px; border-top: 1px solid var(--wd-line); }
.feature-input > button { min-height: 42px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; justify-self: start; padding: 9px 18px; border: 1px solid var(--wd-accent); background: var(--wd-accent); color: #fff; font-weight: 700; cursor: pointer; }
.feature-input > button.danger { border-color: var(--wd-danger); background: var(--wd-danger); }
.feature-input > button:disabled { opacity: .55; cursor: wait; }
.feature-text-input { display: grid; gap: 7px; color: var(--wd-muted); font-size: 13px; }
.feature-text-input > div { display: flex; align-items: center; gap: 8px; min-height: 42px; padding: 0 11px; border: 1px solid var(--wd-line); background: var(--wd-surface-2); color: var(--wd-muted); }
.feature-text-input input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--wd-text); }
.feature-file-picker { position: relative; display: flex; align-items: center; gap: 11px; padding: 13px; border: 1px dashed var(--wd-line); background: var(--wd-surface-2); cursor: pointer; }
.feature-file-picker:hover { border-color: var(--wd-accent); }
.feature-file-picker span { display: grid; gap: 3px; min-width: 0; }
.feature-file-picker strong { overflow: hidden; text-overflow: ellipsis; }
.feature-file-picker small { color: var(--wd-muted); }
.feature-file-picker input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.feature-confirm { display: flex; align-items: center; gap: 9px; color: var(--wd-danger); font-size: 13px; }
.feature-confirm input { width: 17px; height: 17px; accent-color: var(--wd-danger); }
@media (max-width: 720px) {
  .feature-workspace { min-height: 430px; }
  .feature-workspace > header, .feature-output, .feature-keyboard, .feature-attachments, .feature-input { padding-left: 14px; padding-right: 14px; }
  .feature-output { max-height: none; min-height: 180px; }
  .feature-keyboard-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .feature-keyboard button { min-width: 0; overflow-wrap: anywhere; }
}
</style>
