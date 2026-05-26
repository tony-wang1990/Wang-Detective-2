<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { Download, FilePenLine, FolderPlus, FolderOpen, ListChecks, Play, RefreshCw, Save, Server, Square, Terminal, Trash2, Upload, Wifi, Zap } from 'lucide-vue-next';
import { opsDelete, opsDownloadWithProgress, opsGet, opsPost, opsUploadWithProgress, type TransferProgress } from '../api/http';

type Host = {
  id?: string;
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  authType?: string;
  tags?: string;
  hostGroup?: string;
};

type SshSession = {
  sessionId?: string;
  host?: string;
  port?: number;
  username?: string;
  createdAt?: number;
  lastConnectedAt?: number;
  connectCount?: number;
  expiresAt?: number;
  websocketPath?: string;
};

type CommandResult = {
  host?: string;
  name?: string;
  exitStatus?: number;
  timedOut?: boolean;
  durationMillis?: number;
  stdout?: string;
  stderr?: string;
};

type SftpEntry = {
  name?: string;
  path?: string;
  directory?: boolean;
  size?: number;
  modifiedTime?: number;
};

type CommandTemplate = {
  id?: string;
  name?: string;
  command?: string;
  category?: string;
  description?: string;
  riskLevel?: string;
};

type CommandRisk = {
  level: 'LOW' | 'MEDIUM' | 'HIGH';
  label: string;
  reason: string;
};

const SFTP_TRANSFER_LIMIT_BYTES = 50 * 1024 * 1024;
const SFTP_TEXT_PREVIEW_LIMIT_BYTES = 1024 * 1024;
const hosts = ref<Host[]>([]);
const selectedHostId = ref('');
const status = ref('');
const output = ref('等待操作...');
const terminalOutput = ref('尚未连接。选择主机后点击 Web SSH，可在这里直接交互。');
const terminalInput = ref('');
const terminalStatus = ref('未连接');
const terminalRef = ref<HTMLElement | null>(null);
const sshSessions = ref<SshSession[]>([]);
const currentSessionId = ref('');
const currentWebsocketPath = ref('');
const terminalCols = ref(120);
const terminalRows = ref(32);
const command = ref('uname -a && uptime');
const commandHistory = ref<string[]>([]);
const commandTemplates = ref<CommandTemplate[]>([]);
const templateName = ref('');
const templateCategory = ref('常用');
const dangerConfirm = ref('');
const sftpPath = ref('.');
const sftpEntries = ref<SftpEntry[]>([]);
const selectedSftpPath = ref('');
const editorPath = ref('');
const editorContent = ref('');
const newDirName = ref('');
const renameTarget = ref('');
const deleteConfirm = ref('');
const uploadTargetPath = ref('');
const uploadFileInput = ref<HTMLInputElement | null>(null);
const transfer = reactive({
  active: false,
  mode: '',
  filename: '',
  loaded: 0,
  total: 0,
  percent: 0
});
const form = reactive({
  name: '',
  tags: '',
  hostGroup: '默认分组',
  host: '',
  port: 22,
  username: 'root',
  authType: 'password',
  password: '',
  privateKey: '',
  passphrase: ''
});
let terminalWs: WebSocket | null = null;
let resizeTimer: number | undefined;

const currentHost = computed(() => hosts.value.find((host) => host.id === selectedHostId.value));
const hostGroups = computed(() => Array.from(new Set(hosts.value.map((item) => item.hostGroup || '默认分组'))));
const terminalConnected = ref(false);
const commandRisk = computed(() => detectCommandRisk(command.value));
const selectedSftpEntry = computed(() => {
  const path = selectedSftpPath.value || editorPath.value;
  return sftpEntries.value.find((item) => (item.path || item.name) === path);
});
const selectedSftpWarning = computed(() => {
  const entry = selectedSftpEntry.value;
  if (!entry || entry.directory || !entry.size) return '';
  if (entry.size > SFTP_TRANSFER_LIMIT_BYTES) {
    return `当前文件 ${formatBytes(entry.size)}，超过 ${formatBytes(SFTP_TRANSFER_LIMIT_BYTES)} 下载保护阈值，请改用 Web SSH 执行 scp/rsync 或分片处理。`;
  }
  if (entry.size > SFTP_TEXT_PREVIEW_LIMIT_BYTES) {
    return `当前文件 ${formatBytes(entry.size)}，超过 ${formatBytes(SFTP_TEXT_PREVIEW_LIMIT_BYTES)} 文本预览阈值，建议直接下载或用 SSH 查看。`;
  }
  return '';
});
const transferText = computed(() => {
  if (!transfer.filename) return '暂无传输任务';
  const loaded = formatBytes(transfer.loaded);
  const total = transfer.total ? formatBytes(transfer.total) : '未知大小';
  const percent = transfer.percent ? `${transfer.percent}%` : '计算中';
  return `${transfer.mode} ${transfer.filename} · ${loaded} / ${total} · ${percent}`;
});

function credential() {
  if (selectedHostId.value) {
    return { hostId: selectedHostId.value };
  }
  return {
    host: form.host,
    port: Number(form.port || 22),
    username: form.username,
    password: form.password,
    privateKey: form.privateKey,
    passphrase: form.passphrase
  };
}

function errorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : '操作失败';
  if (message.includes('File is larger than 50MB') || message.includes('Maximum upload size exceeded')) {
    return `文件超过 ${formatBytes(SFTP_TRANSFER_LIMIT_BYTES)} 传输保护阈值，当前已阻止。请改用 Web SSH、scp/rsync 或分片压缩后处理。`;
  }
  if (message.includes('File is larger than 1MB')) {
    return `文件超过 ${formatBytes(SFTP_TEXT_PREVIEW_LIMIT_BYTES)} 文本预览阈值，请直接下载或用 Web SSH 查看。`;
  }
  if (message.includes('Cannot download a directory')) {
    return '不能直接下载目录，请先打包目录或选择具体文件。';
  }
  if (message.includes('Cannot read a directory')) {
    return '不能按文本读取目录，请选择具体文件。';
  }
  return message;
}

function formatBytes(bytes?: number) {
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = Number(bytes || 0);
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(index ? 1 : 0)} ${units[index]}`;
}

function formatTime(timestamp?: number) {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleString();
}

function sessionTarget(session: SshSession) {
  return `${session.username || '-'}@${session.host || '-'}:${session.port || 22}`;
}

function joinRemotePath(parent: string, child: string) {
  if (!parent || parent === '.') return child;
  return parent.endsWith('/') ? `${parent}${child}` : `${parent}/${child}`;
}

function downloadName(path: string, disposition?: string) {
  const match = disposition && /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (match) {
    return decodeURIComponent(match[1]);
  }
  const normalized = String(path || 'download.bin').replace(/\\/g, '/');
  const name = normalized.substring(normalized.lastIndexOf('/') + 1);
  return name || 'download.bin';
}

function beginTransfer(mode: string, filename: string, total = 0) {
  transfer.active = true;
  transfer.mode = mode;
  transfer.filename = filename;
  transfer.loaded = 0;
  transfer.total = total;
  transfer.percent = 0;
}

function updateTransfer(progress: TransferProgress) {
  transfer.loaded = progress.loaded || transfer.loaded;
  transfer.total = progress.total || transfer.total;
  transfer.percent = progress.percent ?? (transfer.total ? Math.min(100, Math.round((transfer.loaded / transfer.total) * 100)) : 0);
}

function finishTransfer(message: string) {
  transfer.active = false;
  transfer.percent = 100;
  status.value = message;
}

function chooseEntry(entry: SftpEntry) {
  selectedSftpPath.value = entry.path || entry.name || '';
  if (!entry.directory) {
    editorPath.value = selectedSftpPath.value;
    uploadTargetPath.value = sftpPath.value;
  }
}

function fillHost(host: Host) {
  selectedHostId.value = host.id || '';
  form.name = host.name || '';
  form.tags = host.tags || '';
  form.hostGroup = host.hostGroup || '默认分组';
  form.host = host.host || '';
  form.port = Number(host.port || 22);
  form.username = host.username || 'root';
  form.authType = host.authType || 'password';
}

function loadCommandHistory() {
  try {
    commandHistory.value = JSON.parse(localStorage.getItem('opsCommandHistory') || '[]').slice(0, 6);
  } catch {
    commandHistory.value = [];
  }
}

function rememberCommand(value: string) {
  const text = value.trim();
  if (!text) return;
  commandHistory.value = [text, ...commandHistory.value.filter((item) => item !== text)].slice(0, 6);
  localStorage.setItem('opsCommandHistory', JSON.stringify(commandHistory.value));
}

function applyCommand(value: string) {
  command.value = value;
  dangerConfirm.value = '';
}

function detectCommandRisk(value: string): CommandRisk {
  const text = value.trim().toLowerCase();
  if (!text) {
    return { level: 'LOW', label: '普通命令', reason: '' };
  }
  const highRiskPatterns = [
    /\brm\s+(-[a-z]*r[a-z]*f|-rf|-fr)\b/,
    /\bmkfs(\.|)\w*\b/,
    /\bdd\s+.*\bof=\/dev\//,
    /\bshutdown\b|\breboot\b|\bpoweroff\b|\bhalt\b/,
    /\bdocker\s+(rm|rmi|system\s+prune)\b/,
    /\bdocker\s+compose\s+down\b/,
    /\biptables\s+-f\b|\bufw\s+reset\b/,
    /\bchmod\s+-r\s+777\b|\bchown\s+-r\b/,
    /\btruncate\s+-s\s+0\b/,
    /:\s*\(\)\s*\{\s*:\s*\|\s*:\s*;\s*\}/
  ];
  if (highRiskPatterns.some((pattern) => pattern.test(text))) {
    return { level: 'HIGH', label: '高危命令', reason: '可能删除数据、重启系统、清理容器或破坏网络，需要二次确认。' };
  }
  const mediumRiskPatterns = [
    /\bsystemctl\s+(restart|stop)\b/,
    /\bservice\s+\S+\s+(restart|stop)\b/,
    /\bkill(all)?\b|\bpkill\b/,
    /\bdocker\s+(stop|restart)\b/,
    /\bapt(-get)?\s+(upgrade|dist-upgrade|remove|purge)\b/,
    /\byum\s+(update|remove)\b/
  ];
  if (mediumRiskPatterns.some((pattern) => pattern.test(text))) {
    return { level: 'MEDIUM', label: '中风险命令', reason: '可能影响服务状态或软件包，请确认目标主机后执行。' };
  }
  return { level: 'LOW', label: '普通命令', reason: '' };
}

async function loadCommandTemplates() {
  try {
    const res = await opsGet<CommandTemplate[]>('/templates');
    commandTemplates.value = res.data || [];
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function saveCommandTemplate() {
  const name = templateName.value.trim() || command.value.trim().split(/\s+/).slice(0, 4).join(' ');
  if (!name || !command.value.trim()) {
    status.value = '请先填写模板名称和命令';
    return;
  }
  status.value = '保存命令模板中';
  try {
    await opsPost('/templates', {
      name,
      command: command.value,
      category: templateCategory.value || '常用',
      riskLevel: commandRisk.value.level
    });
    templateName.value = '';
    await loadCommandTemplates();
    status.value = '命令模板已保存';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function loadHosts() {
  try {
    const res = await opsGet<Host[]>('/ssh/hosts');
    hosts.value = res.data || [];
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function loadSessions() {
  try {
    const res = await opsGet<SshSession[]>('/ssh/sessions');
    sshSessions.value = res.data || [];
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function saveHost() {
  status.value = '保存中';
  try {
    const payload = { ...form, port: Number(form.port || 22) };
    await opsPost('/ssh/hosts', payload);
    await loadHosts();
    status.value = '主机已保存';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function testConnection() {
  status.value = '测试连接中';
  try {
    const res = await opsPost<boolean>('/ssh/test', { credential: credential() });
    status.value = res.data ? '连接成功' : '连接失败';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function execCommand() {
  if (!command.value.trim()) {
    status.value = '请先输入命令';
    return;
  }
  if (commandRisk.value.level === 'HIGH' && dangerConfirm.value.trim() !== 'EXECUTE') {
    status.value = '高危命令需要先输入 EXECUTE 后再执行';
    return;
  }
  status.value = '命令执行中';
  try {
    const res = await opsPost<CommandResult>('/ssh/exec', {
      credential: credential(),
      command: command.value,
      timeoutSeconds: 60
    });
    rememberCommand(command.value);
    dangerConfirm.value = '';
    const data = res.data || {};
    output.value = [
      `host: ${data.name || data.host || '-'}`,
      `exit: ${data.exitStatus ?? '-'}`,
      `duration: ${data.durationMillis ?? 0} ms`,
      '',
      data.stdout || '',
      data.stderr ? `\n[stderr]\n${data.stderr}` : ''
    ].join('\n');
    status.value = '命令完成';
  } catch (error) {
    output.value = errorMessage(error);
    status.value = '命令失败';
  }
}

function appendTerminal(text: string) {
  terminalOutput.value += text;
  if (terminalOutput.value.length > 50000) {
    terminalOutput.value = terminalOutput.value.slice(-50000);
  }
  nextTick(() => {
    if (terminalRef.value) {
      terminalRef.value.scrollTop = terminalRef.value.scrollHeight;
    }
  });
}

function closeTerminalSocket() {
  if (terminalWs) {
    terminalWs.close();
    terminalWs = null;
  }
  terminalConnected.value = false;
}

function disconnectTerminal() {
  closeTerminalSocket();
  terminalStatus.value = '已断开';
}

function terminalUrl(websocketPath: string) {
  const token = sessionStorage.getItem('token') || '';
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${scheme}//${window.location.host}${websocketPath}?token=${encodeURIComponent(token)}`;
}

function sendTerminalResize() {
  if (!terminalWs || terminalWs.readyState !== WebSocket.OPEN) {
    return;
  }
  terminalWs.send(`__KD_RESIZE__:${terminalCols.value}:${terminalRows.value}`);
}

function autoResizeTerminal() {
  if (!terminalRef.value) {
    sendTerminalResize();
    return;
  }
  const rect = terminalRef.value.getBoundingClientRect();
  terminalCols.value = Math.max(80, Math.min(220, Math.floor((rect.width - 28) / 8)));
  terminalRows.value = Math.max(18, Math.min(60, Math.floor((rect.height - 28) / 18)));
  sendTerminalResize();
}

function scheduleAutoResize() {
  if (resizeTimer) {
    window.clearTimeout(resizeTimer);
  }
  resizeTimer = window.setTimeout(autoResizeTerminal, 160);
}

function connectTerminal(websocketPath: string, clearOutput = false) {
  closeTerminalSocket();
  currentWebsocketPath.value = websocketPath;
  terminalStatus.value = '连接中';
  if (clearOutput) {
    terminalOutput.value = '正在连接 SSH 会话...\n';
  } else {
    appendTerminal('\n[本地] 正在重连 SSH 会话...\n');
  }
  const ws = new WebSocket(terminalUrl(websocketPath));
  terminalWs = ws;
  ws.onopen = () => {
    terminalConnected.value = true;
    terminalStatus.value = '已连接';
    status.value = 'Web SSH 已连接';
    nextTick(autoResizeTerminal);
    loadSessions();
  };
  ws.onmessage = (event) => appendTerminal(String(event.data || ''));
  ws.onerror = () => {
    terminalStatus.value = '连接错误';
    status.value = 'Web SSH 连接错误';
  };
  ws.onclose = () => {
    if (terminalWs === ws) {
      terminalWs = null;
      terminalConnected.value = false;
      terminalStatus.value = '已断开，可重连';
      loadSessions();
    }
  };
}

async function createSession() {
  closeTerminalSocket();
  status.value = '创建 Web SSH 会话中';
  terminalStatus.value = '连接中';
  terminalOutput.value = '正在创建 SSH 会话...\n';
  try {
    const res = await opsPost<SshSession>('/ssh/session', {
      credential: credential(),
      ttlMinutes: 30
    });
    const websocketPath = res.data?.websocketPath;
    if (!websocketPath) {
      terminalStatus.value = '创建失败';
      status.value = '会话创建失败';
      appendTerminal('后端没有返回 WebSocket 地址。\n');
      return;
    }
    currentSessionId.value = res.data?.sessionId || '';
    currentWebsocketPath.value = websocketPath;
    await loadSessions();
    connectTerminal(websocketPath, true);
  } catch (error) {
    terminalStatus.value = '连接失败';
    status.value = 'Web SSH 连接失败';
    appendTerminal(`[本地] ${errorMessage(error)}\n`);
  }
}

function reconnectSession(session?: SshSession) {
  const targetSession = session || sshSessions.value.find((item) => item.sessionId === currentSessionId.value);
  const websocketPath = targetSession?.websocketPath || currentWebsocketPath.value;
  if (!websocketPath) {
    appendTerminal('\n[本地] 没有可重连的 Web SSH 会话，请先创建会话。\n');
    return;
  }
  currentSessionId.value = targetSession?.sessionId || currentSessionId.value;
  connectTerminal(websocketPath, false);
}

async function deleteSession(session: SshSession) {
  if (!session.sessionId) {
    return;
  }
  try {
    await opsDelete(`/ssh/sessions/${session.sessionId}`);
    if (currentSessionId.value === session.sessionId) {
      disconnectTerminal();
      currentSessionId.value = '';
      currentWebsocketPath.value = '';
    }
    await loadSessions();
    status.value = 'Web SSH 会话已删除';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

function sendTerminalLine() {
  if (!terminalWs || terminalWs.readyState !== WebSocket.OPEN) {
    appendTerminal('\n[本地] 终端未连接，请先点击 Web SSH。\n');
    return;
  }
  terminalWs.send(`${terminalInput.value}\n`);
  terminalInput.value = '';
}

function sendCtrlC() {
  if (terminalWs && terminalWs.readyState === WebSocket.OPEN) {
    terminalWs.send('\u0003');
  }
}

async function listSftp(path = sftpPath.value) {
  status.value = '读取 SFTP 目录中';
  try {
    const res = await opsPost<{ path?: string; entries?: SftpEntry[] }>('/sftp/list', {
      credential: credential(),
      path
    });
    sftpPath.value = res.data?.path || path;
    sftpEntries.value = res.data?.entries || [];
    selectedSftpPath.value = '';
    if (!uploadTargetPath.value) {
      uploadTargetPath.value = sftpPath.value;
    }
    status.value = 'SFTP 已刷新';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function readSftpFile(path = selectedSftpPath.value || editorPath.value) {
  if (!path) {
    status.value = '请选择文件';
    return;
  }
  status.value = '读取文件中';
  try {
    const res = await opsPost<string>('/sftp/read', {
      credential: credential(),
      path
    });
    editorPath.value = path;
    editorContent.value = res.data || '';
    status.value = '文件已读取';
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function writeSftpFile() {
  if (!editorPath.value) {
    status.value = '请先填写文件路径';
    return;
  }
  status.value = '保存文件中';
  try {
    await opsPost('/sftp/write', {
      credential: credential(),
      path: editorPath.value,
      content: editorContent.value
    });
    status.value = '文件已保存';
    await listSftp();
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function downloadSftpFile(path = selectedSftpPath.value || editorPath.value) {
  if (!path) {
    status.value = '请选择要下载的文件';
    return;
  }
  status.value = '下载准备中';
  const entry = sftpEntries.value.find((item) => (item.path || item.name) === path);
  if (entry?.size && entry.size > SFTP_TRANSFER_LIMIT_BYTES) {
    status.value = `文件大小 ${formatBytes(entry.size)}，超过 ${formatBytes(SFTP_TRANSFER_LIMIT_BYTES)} 下载保护阈值；请使用 Web SSH 执行 scp/rsync、tar 分片或压缩后处理。`;
    return;
  }
  beginTransfer('下载', downloadName(path), entry?.size || 0);
  try {
    const result = await opsDownloadWithProgress('/sftp/download', {
      credential: credential(),
      path
    }, updateTransfer);
    const url = URL.createObjectURL(result.blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = downloadName(path, result.filename);
    anchor.click();
    URL.revokeObjectURL(url);
    finishTransfer('文件下载已触发');
  } catch (error) {
    transfer.active = false;
    status.value = errorMessage(error);
  }
}

async function uploadSftpFile() {
  const input = uploadFileInput.value;
  const file = input?.files?.[0];
  if (!selectedHostId.value) {
    status.value = '上传需要先选择保存的主机';
    return;
  }
  if (!file) {
    status.value = '请选择要上传的文件';
    return;
  }
  if (file.size > SFTP_TRANSFER_LIMIT_BYTES) {
    status.value = `上传文件 ${formatBytes(file.size)}，超过 ${formatBytes(SFTP_TRANSFER_LIMIT_BYTES)} 表单上传限制；请使用 Web SSH、scp/rsync 或分片上传。`;
    return;
  }
  const targetPath = uploadTargetPath.value || joinRemotePath(sftpPath.value, file.name);
  status.value = '上传文件中';
  beginTransfer('上传', file.name, file.size);
  try {
    await opsUploadWithProgress(targetPath, selectedHostId.value, file, updateTransfer);
    finishTransfer('文件已上传');
    if (input) {
      input.value = '';
    }
    await listSftp();
  } catch (error) {
    transfer.active = false;
    status.value = errorMessage(error);
  }
}

async function mkdirSftp() {
  const name = newDirName.value.trim();
  if (!name) {
    status.value = '请填写目录名称';
    return;
  }
  const path = name.startsWith('/') ? name : joinRemotePath(sftpPath.value, name);
  status.value = '创建目录中';
  try {
    await opsPost('/sftp/mkdir', {
      credential: credential(),
      path
    });
    newDirName.value = '';
    status.value = '目录已创建';
    await listSftp();
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function deleteSftpPath() {
  const path = selectedSftpPath.value || editorPath.value;
  if (!path) {
    status.value = '请选择要删除的路径';
    return;
  }
  if (deleteConfirm.value.trim() !== 'DELETE') {
    status.value = '删除前请在确认框输入 DELETE';
    return;
  }
  status.value = '删除中';
  try {
    await opsPost('/sftp/delete', {
      credential: credential(),
      path,
      recursive: true
    });
    selectedSftpPath.value = '';
    deleteConfirm.value = '';
    status.value = '路径已删除';
    await listSftp();
  } catch (error) {
    status.value = errorMessage(error);
  }
}

async function renameSftpPath() {
  const path = selectedSftpPath.value || editorPath.value;
  if (!path || !renameTarget.value) {
    status.value = '请选择路径并填写新路径';
    return;
  }
  status.value = '重命名中';
  try {
    await opsPost('/sftp/rename', {
      credential: credential(),
      path,
      targetPath: renameTarget.value
    });
    selectedSftpPath.value = renameTarget.value;
    renameTarget.value = '';
    status.value = '路径已重命名';
    await listSftp();
  } catch (error) {
    status.value = errorMessage(error);
  }
}

onMounted(() => {
  loadHosts();
  loadSessions();
  loadCommandTemplates();
  loadCommandHistory();
  window.addEventListener('resize', scheduleAutoResize);
});
onBeforeUnmount(() => {
  window.removeEventListener('resize', scheduleAutoResize);
  if (resizeTimer) {
    window.clearTimeout(resizeTimer);
  }
  disconnectTerminal();
});
</script>

<template>
  <section class="wd-page">
    <div class="wd-page-title">
      <div>
        <h1>运维终端</h1>
        <p>已迁入 Vue 原生路由，保留主机库、SSH 命令、Web SSH 会话和 SFTP 浏览入口。</p>
      </div>
      <div class="wd-actions">
        <button type="button" @click="loadHosts"><RefreshCw :size="16" />刷新主机</button>
        <button type="button" @click="testConnection"><Wifi :size="16" />测试连接</button>
      </div>
    </div>

    <section class="wd-ops-grid">
      <aside class="wd-card wd-form-card">
        <header><h2><Server :size="17" /> 连接</h2></header>
        <div class="wd-form-grid single">
          <label>
            <span>保存的主机</span>
            <select v-model="selectedHostId" @change="currentHost && fillHost(currentHost)">
              <option value="">手动输入/临时主机</option>
              <option v-for="host in hosts" :key="host.id" :value="host.id">{{ host.name || host.host }}</option>
            </select>
          </label>
          <label><span>主机名称</span><input v-model="form.name" placeholder="例如 tokyo-a1" /></label>
          <label>
            <span>主机分组</span>
            <input v-model="form.hostGroup" list="host-groups" placeholder="默认分组 / 生产 / 测试" />
            <datalist id="host-groups">
              <option v-for="group in hostGroups" :key="group" :value="group" />
            </datalist>
          </label>
          <label><span>标签</span><input v-model="form.tags" placeholder="oci,prod,tokyo" /></label>
          <div class="wd-two">
            <label><span>主机</span><input v-model="form.host" placeholder="1.2.3.4" /></label>
            <label><span>端口</span><input v-model.number="form.port" type="number" /></label>
          </div>
          <label><span>用户</span><input v-model="form.username" /></label>
          <label>
            <span>认证</span>
            <select v-model="form.authType">
              <option value="password">密码</option>
              <option value="privateKey">私钥</option>
            </select>
          </label>
          <label v-if="form.authType === 'password'"><span>密码</span><input v-model="form.password" type="password" /></label>
          <label v-else><span>私钥</span><textarea v-model="form.privateKey" /></label>
          <div class="wd-actions compact">
            <button type="button" @click="saveHost"><Save :size="16" />保存</button>
            <button type="button" @click="createSession"><Terminal :size="16" />Web SSH</button>
          </div>
        </div>
      </aside>

      <main class="wd-ops-workspace">
        <div class="wd-card wd-log-card">
          <header>
            <h2><Terminal :size="17" /> SSH 命令</h2>
            <span>{{ status || '待命' }}</span>
          </header>
          <div class="wd-template-bar">
            <span><ListChecks :size="15" />模板</span>
            <button v-for="item in commandTemplates" :key="item.id || item.name" type="button" @click="applyCommand(item.command || '')">
              {{ item.name }}
            </button>
            <button v-for="item in commandHistory" :key="item" type="button" class="ghost" @click="applyCommand(item)">
              {{ item.length > 18 ? `${item.slice(0, 18)}...` : item }}
            </button>
          </div>
          <div class="wd-template-save">
            <input v-model="templateName" placeholder="保存为模板名称" />
            <input v-model="templateCategory" placeholder="分类" />
            <button type="button" class="ghost" @click="saveCommandTemplate"><Save :size="15" />保存模板</button>
          </div>
          <div class="wd-command-row">
            <input v-model="command" placeholder="输入命令" @keyup.enter="execCommand" />
            <button type="button" @click="execCommand"><Play :size="16" />执行</button>
          </div>
          <div v-if="commandRisk.level !== 'LOW'" class="wd-command-risk" :class="{ high: commandRisk.level === 'HIGH' }">
            <div>
              <strong>{{ commandRisk.label }}</strong>
              <span>{{ commandRisk.reason }}</span>
            </div>
            <input
              v-if="commandRisk.level === 'HIGH'"
              v-model="dangerConfirm"
              placeholder="输入 EXECUTE 后允许执行高危命令"
            />
          </div>
          <pre class="wd-terminal">{{ output }}</pre>
        </div>

        <div class="wd-card wd-log-card">
          <header>
            <h2><Terminal :size="17" /> Web SSH</h2>
            <div class="wd-terminal-controls">
              <span>{{ terminalStatus }}</span>
              <label><small>列</small><input v-model.number="terminalCols" type="number" min="40" max="240" /></label>
              <label><small>行</small><input v-model.number="terminalRows" type="number" min="10" max="80" /></label>
              <button type="button" class="ghost" @click="sendTerminalResize"><RefreshCw :size="15" />同步尺寸</button>
              <button type="button" class="ghost" @click="autoResizeTerminal">自适应</button>
              <button type="button" class="ghost" :disabled="!currentWebsocketPath" @click="reconnectSession()">
                <RefreshCw :size="15" />重连
              </button>
            </div>
          </header>
          <pre ref="terminalRef" class="wd-terminal">{{ terminalOutput }}</pre>
          <div class="wd-command-row terminal-input-row">
            <input v-model="terminalInput" placeholder="输入命令，Enter 发送" @keyup.enter="sendTerminalLine" />
            <button type="button" :disabled="!terminalConnected" @click="sendTerminalLine"><Zap :size="16" />发送</button>
            <button type="button" @click="sendCtrlC"><Square :size="14" />Ctrl+C</button>
            <button type="button" class="ghost" @click="disconnectTerminal">断开</button>
          </div>
          <div class="wd-session-strip">
            <div class="wd-session-title">
              <strong>Web SSH 会话列表</strong>
              <button type="button" class="wd-link-button" @click="loadSessions">刷新</button>
            </div>
            <article v-if="sshSessions.length === 0" class="wd-session-empty">暂无可重连会话</article>
            <article
              v-for="session in sshSessions"
              :key="session.sessionId"
              class="wd-session-card"
              :class="{ active: session.sessionId === currentSessionId }"
            >
              <div>
                <b>{{ sessionTarget(session) }}</b>
                <span>创建 {{ formatTime(session.createdAt) }} · 连接 {{ session.connectCount || 0 }} 次</span>
                <small>最后连接 {{ formatTime(session.lastConnectedAt) }} · 过期 {{ formatTime(session.expiresAt) }}</small>
              </div>
              <div class="wd-row-actions">
                <button type="button" @click="reconnectSession(session)">重连</button>
                <button type="button" class="danger-soft" @click="deleteSession(session)">删除</button>
              </div>
            </article>
          </div>
        </div>

        <div class="wd-card">
          <header>
            <h2><FolderOpen :size="17" /> SFTP 浏览</h2>
            <button type="button" @click="listSftp()"><RefreshCw :size="16" />刷新</button>
          </header>
          <div class="wd-command-row">
            <input v-model="sftpPath" placeholder="远程目录，例如 /root" @keyup.enter="listSftp()" />
            <button type="button" @click="listSftp()">打开</button>
          </div>
          <div class="wd-sftp-toolbar">
            <div class="wd-inline-field">
              <input v-model="newDirName" placeholder="新目录名" />
              <button type="button" @click="mkdirSftp"><FolderPlus :size="16" />新建目录</button>
            </div>
            <div class="wd-inline-field">
              <input v-model="renameTarget" placeholder="新路径/新名称" />
              <button type="button" @click="renameSftpPath"><FilePenLine :size="16" />重命名</button>
            </div>
            <button type="button" class="ghost" @click="readSftpFile()"><FilePenLine :size="16" />读取</button>
            <button type="button" class="ghost" :disabled="transfer.active" @click="downloadSftpFile()"><Download :size="16" />下载</button>
            <button type="button" class="danger" @click="deleteSftpPath"><Trash2 :size="16" />删除</button>
          </div>
          <div class="wd-danger-confirm">
            <span>删除确认</span>
            <input v-model="deleteConfirm" placeholder="输入 DELETE 后允许删除选中路径" />
            <small>{{ selectedSftpPath || editorPath || '未选择路径' }}</small>
          </div>
          <p v-if="selectedSftpWarning" class="wd-limit-tip">{{ selectedSftpWarning }}</p>
          <table class="wd-table">
            <thead><tr><th>名称</th><th>类型</th><th>大小</th><th>路径</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-if="sftpEntries.length === 0"><td colspan="5">暂无目录数据</td></tr>
              <tr
                v-for="entry in sftpEntries"
                :key="entry.path || entry.name"
                :class="{ selected: selectedSftpPath === (entry.path || entry.name) }"
                @click="chooseEntry(entry)"
                @dblclick="entry.directory ? listSftp(entry.path) : readSftpFile(entry.path)"
              >
                <td>{{ entry.name }}</td>
                <td>{{ entry.directory ? '目录' : '文件' }}</td>
                <td>{{ entry.directory ? '-' : formatBytes(entry.size) }}</td>
                <td>{{ entry.path }}</td>
                <td>
                  <button v-if="entry.directory" type="button" class="wd-link-button" @click.stop="listSftp(entry.path)">打开</button>
                  <button v-else type="button" class="wd-link-button" @click.stop="readSftpFile(entry.path)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="wd-editor">
            <div class="wd-inline-field">
              <input v-model="editorPath" placeholder="文件路径，例如 /root/app.log" />
              <button type="button" @click="readSftpFile(editorPath)"><FilePenLine :size="16" />读取文件</button>
              <button type="button" @click="writeSftpFile"><Save :size="16" />保存文件</button>
            </div>
            <textarea v-model="editorContent" placeholder="选择文件后可在这里查看或编辑文本内容"></textarea>
          </div>
          <div class="wd-upload-row">
            <input ref="uploadFileInput" type="file" />
            <input v-model="uploadTargetPath" placeholder="上传目标路径，留空则使用当前目录/文件名" />
            <button type="button" :disabled="transfer.active" @click="uploadSftpFile"><Upload :size="16" />上传</button>
          </div>
          <div v-if="transfer.filename" class="wd-transfer-progress" :class="{ active: transfer.active }">
            <div>
              <b>{{ transfer.active ? '传输中' : '最近传输' }}</b>
              <span>{{ transferText }}</span>
            </div>
            <div class="wd-progress-track">
              <i :style="{ width: `${transfer.percent || 0}%` }"></i>
            </div>
          </div>
          <p class="wd-muted-line">提示：单文件上传/下载保护阈值为 {{ formatBytes(SFTP_TRANSFER_LIMIT_BYTES) }}；上传接口目前需要选择“保存的主机”，临时手动主机可先保存后再上传。</p>
        </div>
      </main>
    </section>
  </section>
</template>
