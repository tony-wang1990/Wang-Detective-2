<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { Bot, Eraser, Send } from 'lucide-vue-next';

const sessionId = `web-${crypto.randomUUID ? crypto.randomUUID() : Date.now()}`;
const model = ref('deepseek-ai/DeepSeek-R1-Distill-Qwen-7B');
const enableInternet = ref(false);
const input = ref('');
const messages = ref<{ role: 'user' | 'ai'; content: string }[]>([]);
const loading = ref(false);
let abortController: AbortController | null = null;

async function ask() {
  const text = input.value.trim();
  if (!text) return;
  messages.value.push({ role: 'user', content: text });
  input.value = '';
  loading.value = true;
  const ai = { role: 'ai' as const, content: '' };
  messages.value.push(ai);
  abortController?.abort();
  abortController = new AbortController();
  try {
    const params = new URLSearchParams({
      message: text,
      model: model.value,
      sessionId,
      enableInternet: String(enableInternet.value)
    });
    const response = await fetch(`/chat/stream?${params.toString()}`, {
      headers: sessionStorage.getItem('token') ? { Authorization: `Bearer ${sessionStorage.getItem('token')}` } : {},
      signal: abortController.signal
    });
    if (!response.ok || !response.body) throw new Error(`AI 接口 ${response.status}`);
    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split(/\r?\n\r?\n/);
      buffer = parts.pop() || '';
      ai.content += parts.map(parseSseBlock).join('');
    }
    ai.content += parseSseBlock(buffer);
  } catch (err) {
    if ((err as DOMException)?.name !== 'AbortError') {
      ai.content = err instanceof Error ? err.message : 'AI 请求失败';
    }
  } finally {
    loading.value = false;
    abortController = null;
  }
}

async function clearSession() {
  abortController?.abort();
  messages.value = [];
  const token = sessionStorage.getItem('token');
  await fetch(`/chat/removeCache?sessionId=${encodeURIComponent(sessionId)}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  }).catch(() => undefined);
}

function parseSseBlock(block: string) {
  return block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
}

onBeforeUnmount(() => {
  abortController?.abort();
});
</script>

<template>
  <section class="wd-page">
    <div class="wd-page-title">
      <div>
        <h1>AI 聊天室</h1>
        <p>保留原有流式问答能力，迁入新版控制台布局。</p>
      </div>
      <div class="wd-actions">
        <button type="button" class="danger" @click="clearSession"><Eraser :size="16" />清空</button>
      </div>
    </div>

    <div class="wd-card wd-chat">
      <header>
        <h2><Bot :size="17" /> 对话</h2>
        <label class="wd-check"><input v-model="enableInternet" type="checkbox" />联网搜索</label>
      </header>
      <div class="wd-chat-body">
        <div v-if="messages.length === 0" class="wd-empty">输入问题开始对话。</div>
        <article v-for="(message, index) in messages" :key="index" :class="['wd-bubble', message.role]">
          <b>{{ message.role === 'user' ? '你' : 'W-探长 AI' }}</b>
          <p>{{ message.content || (loading && message.role === 'ai' ? '思考中...' : '') }}</p>
        </article>
      </div>
      <footer class="wd-chat-input">
        <input v-model="model" aria-label="model" />
        <textarea v-model="input" placeholder="输入问题，按 Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="ask" />
        <button type="button" :disabled="loading" @click="ask"><Send :size="16" />发送</button>
      </footer>
    </div>
  </section>
</template>
