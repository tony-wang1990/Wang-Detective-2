<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  CheckCircle2,
  Cpu,
  ExternalLink,
  HardDrive,
  Network,
  Play,
  Power,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Square,
  Terminal,
  Trash2,
  UploadCloud,
  UserRound,
  Zap
} from 'lucide-vue-next';
import { apiForm, apiPost, notifyGlobal, type PageResult } from '../api/http';

type Row = {
  id?: string;
  username?: string;
  cfgName?: string;
  userName?: string;
  tenantName?: string;
  region?: string;
  regionName?: string;
  enableCreate?: number;
  isEnableCreate?: boolean;
  createTime?: string;
  [key: string]: unknown;
};

type VnicInfo = {
  vnicId?: string;
  name?: string;
};

type InstanceInfo = {
  ocId?: string;
  region?: string;
  name?: string;
  publicIp?: string[];
  shape?: string;
  enableChangeIp?: number;
  ocpus?: string;
  memory?: string;
  bootVolumeSize?: string;
  createTime?: string;
  state?: string;
  availabilityDomain?: string;
  vnicList?: VnicInfo[];
  [key: string]: unknown;
};

type CfCfg = {
  cfCfgId?: string;
  domain?: string;
};

type NetLoadBalancer = {
  name?: string;
  status?: string;
  publicIp?: string;
};

type OciDetail = {
  userId?: string;
  tenantId?: string;
  fingerprint?: string;
  privateKeyPath?: string;
  region?: string;
  instanceList?: InstanceInfo[];
  cfCfgList?: CfCfg[];
  nlbList?: NetLoadBalancer[];
};

type InstanceCfg = {
  instanceName?: string;
  ipv6?: string;
  ocpus?: string;
  memory?: string;
  bootVolumeSize?: string;
  bootVolumeVpu?: string;
  shape?: string;
};

type DialogValue = string | number | boolean;

type DialogField = {
  key: string;
  label: string;
  value: DialogValue;
  type?: 'text' | 'number' | 'password' | 'textarea' | 'checkbox';
  placeholder?: string;
  help?: string;
  min?: number;
};

type ActionDialog = {
  title: string;
  description: string;
  target?: string;
  actionLabel: string;
  busyKey: string;
  danger?: boolean;
  refreshDetail?: boolean;
  fields: DialogField[];
  onConfirm: (values: Record<string, DialogValue>) => Promise<void>;
  afterSuccess?: () => void;
};

const loading = ref(false);
const keyword = ref('');
const rows = ref<Row[]>([]);
const total = ref(0);
const error = ref('');
const notice = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const isEnableCreate = ref<string>('');
const selectedIds = ref<string[]>([]);
const selectedDetail = ref<Row | null>(null);
const detail = ref<OciDetail | null>(null);
const selectedInstanceCfg = ref<InstanceCfg | null>(null);
const detailLoading = ref(false);
const actionBusy = ref('');
const showAddForm = ref(false);
const showCreateForm = ref(false);
const keyFile = ref<File | null>(null);
const actionDialog = ref<ActionDialog | null>(null);

const addForm = reactive({
  username: '',
  ociCfgStr: ''
});

const createForm = reactive({
  ocpus: '1',
  memory: '6',
  disk: 50,
  architecture: 'ARM',
  interval: 80,
  createNumbers: 1,
  operationSystem: 'Canonical Ubuntu',
  rootPassword: ''
});

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const selectedCount = computed(() => selectedIds.value.length);
const selectedCfgId = computed(() => selectedDetail.value ? rowId(selectedDetail.value) : '');
const detailInstances = computed(() => detail.value?.instanceList || []);
const detailCfCfgs = computed(() => detail.value?.cfCfgList || []);
const detailNlbs = computed(() => detail.value?.nlbList || []);

const columns = [
  { key: 'username', label: '配置名称' },
  { key: 'tenantName', label: '租户' },
  { key: 'region', label: '区域' },
  { key: 'enableCreate', label: '开机任务' },
  { key: 'createTime', label: '创建时间' }
];

function rowId(row: Row) {
  return String(row.id || '');
}

function displayName(row: Row) {
  return String(row.username || row.cfgName || row.userName || '-');
}

function cell(row: Row, key: string) {
  if (key === 'username') return displayName(row);
  if (key === 'region') {
    const region = row.region || '-';
    return row.regionName ? `${region} (${row.regionName})` : String(region);
  }
  if (key === 'enableCreate') {
    const enabled = Number(row.enableCreate ?? 0) === 1 || row.isEnableCreate === true;
    return enabled ? '执行中' : '无任务';
  }
  const value = row[key];
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}

function statusClass(row: Row) {
  const enabled = Number(row.enableCreate ?? 0) === 1 || row.isEnableCreate === true;
  return enabled ? 'success' : 'muted';
}

function instanceId(instance: InstanceInfo) {
  return String(instance.ocId || '');
}

function instanceLabel(instance: InstanceInfo) {
  return String(instance.name || instance.ocId || '-');
}

function publicIps(instance: InstanceInfo) {
  return (instance.publicIp || []).filter(Boolean).join(', ') || '-';
}

function firstVnicId(instance: InstanceInfo) {
  return instance.vnicList?.find((vnic) => vnic.vnicId)?.vnicId || '';
}

function stateClass(state?: string) {
  const normalized = String(state || '').toUpperCase();
  if (['RUNNING', 'ACTIVE'].includes(normalized)) return 'success';
  if (['STOPPED', 'TERMINATED'].includes(normalized)) return 'muted';
  if (['STOPPING', 'STARTING', 'PROVISIONING'].includes(normalized)) return 'warning';
  return 'info';
}

function actionKey(action: string, id?: string) {
  return `${action}:${id || ''}`;
}

function isBusy(action: string, id?: string) {
  return actionBusy.value === actionKey(action, id);
}

async function runAction(label: string, key: string, fn: () => Promise<void>, refreshDetail = true) {
  actionBusy.value = key;
  error.value = '';
  try {
    await fn();
    notice.value = `${label} 已提交`;
    notifyGlobal(`${label} 已提交`, 'success');
    if (refreshDetail && selectedDetail.value) {
      await loadDetails(true);
    }
    await load();
    return true;
  } catch (err) {
    error.value = err instanceof Error ? err.message : `${label} 失败`;
    notifyGlobal(error.value, 'error');
    return false;
  } finally {
    actionBusy.value = '';
  }
}

function openActionDialog(dialog: ActionDialog) {
  error.value = '';
  actionDialog.value = dialog;
}

function closeActionDialog() {
  if (actionBusy.value) return;
  actionDialog.value = null;
}

function dialogString(values: Record<string, DialogValue>, key: string) {
  return String(values[key] ?? '').trim();
}

function dialogNumber(values: Record<string, DialogValue>, key: string, fallback = 0) {
  const value = Number(values[key]);
  return Number.isFinite(value) ? value : fallback;
}

function dialogBoolean(values: Record<string, DialogValue>, key: string) {
  return values[key] === true;
}

function dialogFieldText(field: DialogField) {
  return String(field.value ?? '');
}

function updateDialogField(field: DialogField, event: Event) {
  const target = event.target as HTMLInputElement | HTMLTextAreaElement | null;
  if (!target) return;
  field.value = target.value;
}

function updateDialogCheckbox(field: DialogField, event: Event) {
  const target = event.target as HTMLInputElement | null;
  if (!target) return;
  field.value = target.checked;
}

async function submitActionDialog() {
  const current = actionDialog.value;
  if (!current) return;
  const values = current.fields.reduce<Record<string, DialogValue>>((acc, field) => {
    acc[field.key] = field.value;
    return acc;
  }, {});
  const success = await runAction(
    current.actionLabel,
    current.busyKey,
    () => current.onConfirm(values),
    current.refreshDetail ?? true
  );
  if (success) {
    actionDialog.value = null;
    current.afterSuccess?.();
  }
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
    const res = await apiPost<PageResult<Row>>('/oci/userPage', {
      keyword: keyword.value,
      currentPage: currentPage.value,
      pageSize: pageSize.value,
      isEnableCreate: isEnableCreate.value === '' ? null : Number(isEnableCreate.value)
    });
    rows.value = res.data?.records || [];
    total.value = Number(res.data?.total || rows.value.length || 0);
    selectedIds.value = selectedIds.value.filter((id) => rows.value.some((row) => rowId(row) === id));
    notice.value = `已刷新：${new Date().toLocaleTimeString()}`;
  } catch (err) {
    error.value = err instanceof Error ? err.message : '配置列表读取失败';
  } finally {
    loading.value = false;
  }
}

async function checkAlive() {
  await runAction('一键测活', 'check-alive', async () => {
    const res = await apiPost<unknown>('/oci/checkAlive', {});
    notice.value = res.msg || '测活完成';
  }, false);
}

function onKeyFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  keyFile.value = input.files?.[0] || null;
}

async function addConfig() {
  if (!addForm.username.trim() || !addForm.ociCfgStr.trim() || !keyFile.value) {
    error.value = '请填写配置名称、OCI config 内容并选择私钥文件';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const form = new FormData();
    form.append('username', addForm.username.trim());
    form.append('ociCfgStr', addForm.ociCfgStr.trim());
    form.append('file', keyFile.value);
    const res = await apiForm<void>('/oci/addCfg', form);
    notice.value = res.msg || '配置添加完成';
    addForm.username = '';
    addForm.ociCfgStr = '';
    keyFile.value = null;
    showAddForm.value = false;
    await load();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '配置添加失败';
  } finally {
    loading.value = false;
  }
}

async function renameConfig(row: Row) {
  const id = rowId(row);
  if (!id) return;
  openActionDialog({
    title: '修改配置名称',
    description: '只修改本地显示名称，不会改动 OCI 侧资源。',
    target: displayName(row),
    actionLabel: '修改配置名称',
    busyKey: actionKey('rename-cfg', id),
    refreshDetail: false,
    fields: [{ key: 'name', label: '新配置名称', value: displayName(row), placeholder: '例如 oracle-seoul-a1' }],
    onConfirm: async (values) => {
      const nextName = dialogString(values, 'name');
      if (!nextName) throw new Error('请填写新的配置名称');
      await apiPost('/oci/updateCfgName', { cfgId: id, updateCfgName: nextName });
    }
  });
}

async function deleteSelected() {
  if (!selectedIds.value.length) return;
  const idList = [...selectedIds.value];
  openActionDialog({
    title: '删除配置',
    description: '此操作会删除配置记录和对应密钥数据，请确认这些配置已经不再使用。',
    target: `${idList.length} 个配置`,
    actionLabel: '删除配置',
    busyKey: 'delete-selected',
    danger: true,
    refreshDetail: false,
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/removeCfg', { idList });
      selectedIds.value = [];
    }
  });
}

async function stopCreate(row: Row) {
  const id = rowId(row);
  if (!id) return;
  openActionDialog({
    title: '停止开机任务',
    description: '会停止当前配置正在执行的抢机/开机任务，不会删除已经创建成功的实例。',
    target: displayName(row),
    actionLabel: '停止开机任务',
    busyKey: actionKey('stop-create', id),
    refreshDetail: false,
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/stopCreate', { userId: id });
    }
  });
}

async function releaseSecurityRule(row: Row) {
  const id = rowId(row);
  if (!id) return;
  openActionDialog({
    title: '安全列表放行',
    description: '会调用 OCI 接口修改安全列表规则，用于放行实例访问。请确认当前租户允许此类变更。',
    target: displayName(row),
    actionLabel: '安全列表放行',
    busyKey: actionKey('release-rule', id),
    danger: true,
    refreshDetail: false,
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/releaseSecurityRule', { ociCfgId: id });
    }
  });
}

async function loadDetails(force: boolean) {
  const cfgId = selectedCfgId.value;
  if (!cfgId) return;
  detailLoading.value = true;
  selectedInstanceCfg.value = null;
  try {
    const res = await apiPost<OciDetail>('/oci/details', {
      cfgId,
      cleanReLaunchDetails: force
    });
    detail.value = res.data || {};
    notice.value = force ? '已刷新 OCI 实时数据' : '已读取配置详情';
  } catch (err) {
    error.value = err instanceof Error ? err.message : '读取 OCI 实时数据失败';
  } finally {
    detailLoading.value = false;
  }
}

async function openDetails(row: Row) {
  const id = rowId(row);
  if (!id) return;
  selectedDetail.value = row;
  detail.value = null;
  await loadDetails(true);
}

async function createForSelected() {
  const userIds = selectedIds.value.length ? selectedIds.value : selectedDetail.value ? [selectedCfgId.value] : [];
  if (!userIds.length) {
    error.value = '请至少选择一个 OCI 配置，或先打开某个配置详情';
    return;
  }
  if (!createForm.rootPassword.trim()) {
    error.value = '请先填写 root 密码';
    return;
  }
  await runAction('批量创建实例', 'create-instance-batch', async () => {
    await apiPost('/oci/createInstanceBatch', {
      userIds,
      instanceInfo: {
        ...createForm,
        disk: Number(createForm.disk),
        interval: Number(createForm.interval),
        createNumbers: Number(createForm.createNumbers)
      }
    });
    showCreateForm.value = false;
  }, false);
}

async function updateInstanceState(instance: InstanceInfo, action: 'START' | 'STOP' | 'RESET') {
  const id = instanceId(instance);
  const label = action === 'START' ? '启动实例' : action === 'STOP' ? '停止实例' : '重启实例';
  if (!id) return;
  openActionDialog({
    title: label,
    description: '会调用 OCI Compute 实例状态接口，请确认目标实例无正在执行的其他变更。',
    target: instanceLabel(instance),
    actionLabel: label,
    busyKey: actionKey(action, id),
    danger: action !== 'START',
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/updateInstanceState', { ociCfgId: selectedCfgId.value, instanceId: id, action });
    }
  });
}

async function renameInstance(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '修改实例名称',
    description: '会修改 OCI 实例 Display Name，便于后续识别实例。',
    target: instanceLabel(instance),
    actionLabel: '修改实例名称',
    busyKey: actionKey('rename-instance', id),
    fields: [{ key: 'name', label: '新实例名称', value: instanceLabel(instance), placeholder: '例如 seoul-a1-main' }],
    onConfirm: async (values) => {
      const name = dialogString(values, 'name');
      if (!name) throw new Error('请填写新的实例名称');
      await apiPost('/oci/updateInstanceName', { ociCfgId: selectedCfgId.value, instanceId: id, name });
    }
  });
}

async function changeIp(instance: InstanceInfo) {
  const id = instanceId(instance);
  const vnicId = firstVnicId(instance);
  if (!id || !vnicId) {
    error.value = '此实例未返回可用 VNIC，无法换 IP';
    return;
  }
  openActionDialog({
    title: '提交换 IP 任务',
    description: '会为实例重新分配公网 IP。CIDR 可填写多个，用英文逗号分隔。',
    target: instanceLabel(instance),
    actionLabel: '提交换 IP 任务',
    busyKey: actionKey('change-ip', id),
    danger: true,
    fields: [
      { key: 'cidr', label: '允许临时 SSH 的 CIDR', value: '0.0.0.0/0', placeholder: '0.0.0.0/0,1.2.3.4/32' },
      { key: 'ttl', label: 'Cloudflare TTL', value: 60, type: 'number', min: 1, help: '未启用 DNS 同步时仅作为预留参数。' }
    ],
    onConfirm: async (values) => {
      const cidr = dialogString(values, 'cidr');
      if (!cidr) throw new Error('请填写 CIDR');
      await apiPost('/oci/changeIp', {
        ociCfgId: selectedCfgId.value,
        instanceId: id,
        vnicId,
        cidrList: cidr.split(',').map((item) => item.trim()).filter(Boolean),
        changeCfDns: false,
        enableProxy: false,
        ttl: dialogNumber(values, 'ttl', 60)
      });
    }
  });
}

async function stopChangeIp(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '停止换 IP 任务',
    description: '会停止此实例后台持续换 IP 任务。',
    target: instanceLabel(instance),
    actionLabel: '停止换 IP 任务',
    busyKey: actionKey('stop-change-ip', id),
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/stopChangeIp', { instanceId: id });
    }
  });
}

async function createIpv6(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '创建 IPv6',
    description: '会调用 OCI 网络接口为实例创建 IPv6，请确认当前子网支持 IPv6。',
    target: instanceLabel(instance),
    actionLabel: '创建 IPv6',
    busyKey: actionKey('ipv6', id),
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/createIpv6', { ociCfgId: selectedCfgId.value, instanceId: id });
    }
  });
}

async function startVnc(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  await runAction('启动 VNC', actionKey('vnc', id), async () => {
    const res = await apiPost<string>('/oci/startVnc', { ociCfgId: selectedCfgId.value, instanceId: id });
    notice.value = res.data || res.msg || 'VNC 已开启';
  }, false);
}

async function autoRescue(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '自动救援',
    description: '会创建救援流程相关资源。建议先确认原实例数据已经备份。',
    target: instanceLabel(instance),
    actionLabel: '自动救援',
    busyKey: actionKey('rescue', id),
    danger: true,
    fields: [
      { key: 'name', label: '救援实例名称', value: `${instanceLabel(instance)}-rescue` },
      { key: 'keepBackupVolume', label: '保留备份卷', value: true, type: 'checkbox', help: '关闭后流程结束会尝试自动清理备份卷。' }
    ],
    onConfirm: async (values) => {
      const name = dialogString(values, 'name');
      if (!name) throw new Error('请填写救援实例名称');
      await apiPost('/oci/autoRescue', {
        ociCfgId: selectedCfgId.value,
        instanceId: id,
        name,
        keepBackupVolume: dialogBoolean(values, 'keepBackupVolume')
      });
    }
  });
}

async function enable500M(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '开启 500M',
    description: '会创建/调整负载均衡相关资源以实现 500M 入口能力。',
    target: instanceLabel(instance),
    actionLabel: '开启 500M',
    busyKey: actionKey('500m-on', id),
    fields: [{ key: 'sshPort', label: '实例 SSH 端口', value: 22, type: 'number', min: 1 }],
    onConfirm: async (values) => {
      const sshPort = dialogNumber(values, 'sshPort', 22);
      if (!sshPort) throw new Error('请填写有效 SSH 端口');
      await apiPost('/oci/oneClick500M', { ociCfgId: selectedCfgId.value, instanceId: id, sshPort });
    }
  });
}

async function close500M(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '关闭 500M',
    description: '会关闭 500M 相关链路。可选择是否保留负载均衡器和 NAT 网关，方便后续快速恢复。',
    target: instanceLabel(instance),
    actionLabel: '关闭 500M',
    busyKey: actionKey('500m-off', id),
    danger: true,
    fields: [{ key: 'retain', label: '保留负载均衡器和 NAT 网关', value: true, type: 'checkbox' }],
    onConfirm: async (values) => {
      const retain = dialogBoolean(values, 'retain');
      await apiPost('/oci/oneClickClose500M', {
        ociCfgId: selectedCfgId.value,
        instanceId: id,
        retainBl: retain,
        retainNatGw: retain
      });
    }
  });
}

async function getInstanceCfg(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  await runAction('读取实例配置', actionKey('cfg-info', id), async () => {
    const res = await apiPost<InstanceCfg>('/oci/getInstanceCfgInfo', { ociCfgId: selectedCfgId.value, instanceId: id });
    selectedInstanceCfg.value = res.data || {};
  }, false);
}

async function updateInstanceCfg(instance: InstanceInfo) {
  const id = instanceId(instance);
  const current = selectedInstanceCfg.value;
  if (!id) return;
  openActionDialog({
    title: '调整 CPU/内存',
    description: '会调用 OCI 实例配置变更接口。部分规格要求实例停止后才能调整。',
    target: instanceLabel(instance),
    actionLabel: '调整 CPU/内存',
    busyKey: actionKey('resize-cfg', id),
    fields: [
      { key: 'ocpus', label: 'OCPU 数量', value: current?.ocpus || instance.ocpus || '1' },
      { key: 'memory', label: '内存 GB', value: current?.memory || instance.memory || '6' }
    ],
    onConfirm: async (values) => {
      const ocpus = dialogString(values, 'ocpus');
      const memory = dialogString(values, 'memory');
      if (!ocpus || !memory) throw new Error('请填写 OCPU 和内存');
      await apiPost('/oci/updateInstanceCfg', { ociCfgId: selectedCfgId.value, instanceId: id, ocpus, memory });
    }
  });
}

async function updateShape(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '修改 Shape',
    description: '会调用 OCI Shape 变更接口。请确认目标 Shape 在当前区域/可用域有配额。',
    target: instanceLabel(instance),
    actionLabel: '修改 Shape',
    busyKey: actionKey('shape', id),
    fields: [{ key: 'shape', label: 'Shape', value: selectedInstanceCfg.value?.shape || instance.shape || 'VM.Standard.A1.Flex' }],
    onConfirm: async (values) => {
      const shape = dialogString(values, 'shape');
      if (!shape) throw new Error('请填写 Shape');
      await apiPost('/oci/updateInstanceShape', { ociCfgId: selectedCfgId.value, instanceId: id, shape });
    }
  });
}

async function updateBootVolume(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '调整引导卷',
    description: '会修改引导卷大小和 VPU。引导卷通常只支持扩容，请谨慎填写。',
    target: instanceLabel(instance),
    actionLabel: '调整引导卷',
    busyKey: actionKey('boot-volume', id),
    fields: [
      { key: 'bootVolumeSize', label: '引导卷大小 GB', value: selectedInstanceCfg.value?.bootVolumeSize || instance.bootVolumeSize || '50' },
      { key: 'bootVolumeVpu', label: '引导卷 VPU', value: selectedInstanceCfg.value?.bootVolumeVpu || '10' }
    ],
    onConfirm: async (values) => {
      const bootVolumeSize = dialogString(values, 'bootVolumeSize');
      const bootVolumeVpu = dialogString(values, 'bootVolumeVpu');
      if (!bootVolumeSize || !bootVolumeVpu) throw new Error('请填写引导卷大小和 VPU');
      await apiPost('/oci/updateBootVolumeCfg', {
        ociCfgId: selectedCfgId.value,
        instanceId: id,
        bootVolumeSize,
        bootVolumeVpu
      });
    }
  });
}

async function terminateInstance(instance: InstanceInfo) {
  const id = instanceId(instance);
  if (!id) return;
  openActionDialog({
    title: '终止实例 - 发送验证码',
    description: '第一步会向通知渠道发送终止验证码。收到验证码后继续填写并确认终止。',
    target: instanceLabel(instance),
    actionLabel: '发送终止验证码',
    busyKey: actionKey('captcha', id),
    danger: true,
    refreshDetail: false,
    fields: [],
    onConfirm: async () => {
      await apiPost('/oci/sendCaptcha', { ociCfgId: selectedCfgId.value, instanceId: id });
    },
    afterSuccess: () => {
      openActionDialog({
        title: '终止实例',
        description: '请输入刚收到的验证码。终止实例是高危操作，请再次确认目标实例。',
        target: instanceLabel(instance),
        actionLabel: '终止实例',
        busyKey: actionKey('terminate', id),
        danger: true,
        fields: [
          { key: 'captcha', label: '终止验证码', value: '', placeholder: '请输入验证码' },
          { key: 'preserveBootVolume', label: '保留引导卷', value: true, type: 'checkbox' }
        ],
        onConfirm: async (values) => {
          const captcha = dialogString(values, 'captcha');
          if (!captcha) throw new Error('请填写终止验证码');
          await apiPost('/oci/terminateInstance', {
            ociCfgId: selectedCfgId.value,
            instanceId: id,
            preserveBootVolume: dialogBoolean(values, 'preserveBootVolume') ? 1 : 0,
            captcha
          });
        }
      });
    }
  });
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
        <h1>配置列表</h1>
        <p>OCI API 配置、租户和实例资源入口，支持从详情页直接执行真实实例操作与运维闭环。</p>
      </div>
      <div class="wd-actions">
        <button type="button" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</button>
        <button type="button" :disabled="isBusy('check-alive')" @click="checkAlive"><CheckCircle2 :size="16" />一键测活</button>
        <button type="button" class="ghost" @click="showCreateForm = !showCreateForm"><Zap :size="16" />批量开机</button>
        <button type="button" class="ghost" @click="showAddForm = !showAddForm"><UploadCloud :size="16" />新增配置</button>
        <button type="button" class="danger" :disabled="selectedCount === 0 || isBusy('delete-selected')" @click="deleteSelected">
          <Trash2 :size="16" />删除 {{ selectedCount || '' }}
        </button>
      </div>
    </div>

    <div v-if="showCreateForm" class="wd-card wd-form-card">
      <header>
        <h2><Zap :size="17" /> 批量创建实例</h2>
        <button type="button" class="ghost" @click="showCreateForm = false">收起</button>
      </header>
      <div class="wd-form-grid">
        <label><span>OCPU</span><input v-model="createForm.ocpus" /></label>
        <label><span>内存 GB</span><input v-model="createForm.memory" /></label>
        <label><span>硬盘 GB</span><input v-model.number="createForm.disk" type="number" min="50" /></label>
        <label>
          <span>架构</span>
          <select v-model="createForm.architecture">
            <option value="ARM">ARM</option>
            <option value="AMD">AMD</option>
            <option value="AMD_E5">AMD_E5</option>
          </select>
        </label>
        <label><span>重试间隔秒</span><input v-model.number="createForm.interval" type="number" min="10" /></label>
        <label><span>创建数量</span><input v-model.number="createForm.createNumbers" type="number" min="1" /></label>
        <label><span>系统镜像</span><input v-model="createForm.operationSystem" /></label>
        <label><span>root 密码</span><input v-model="createForm.rootPassword" type="password" autocomplete="new-password" /></label>
      </div>
      <div class="wd-actions compact">
        <button type="button" :disabled="isBusy('create-instance-batch')" @click="createForSelected">
          <Play :size="16" />按已选配置创建实例
        </button>
        <span class="wd-help-line">未勾选配置时，将使用当前打开详情的配置。</span>
      </div>
    </div>

    <div v-if="showAddForm" class="wd-card wd-form-card">
      <header>
        <h2><UploadCloud :size="17" /> 新增 OCI 配置</h2>
        <button type="button" class="ghost" @click="showAddForm = false">收起</button>
      </header>
      <div class="wd-form-grid">
        <label>
          <span>配置名称</span>
          <input v-model="addForm.username" placeholder="例如 oracle-seoul-a1" />
        </label>
        <label>
          <span>私钥文件</span>
          <input type="file" @change="onKeyFileChange" />
        </label>
        <label class="wide">
          <span>OCI config 内容</span>
          <textarea v-model="addForm.ociCfgStr" placeholder="[DEFAULT]&#10;user=ocid1.user...&#10;fingerprint=...&#10;tenancy=ocid1.tenancy...&#10;region=ap-seoul-1"></textarea>
        </label>
      </div>
      <div class="wd-actions compact">
        <button type="button" :disabled="loading" @click="addConfig"><UploadCloud :size="16" />提交并校验</button>
      </div>
    </div>

    <div class="wd-card wd-table-card">
      <header>
        <h2><UserRound :size="17" /> OCI 配置</h2>
        <div class="wd-table-tools">
          <label class="wd-inline-search">
            <input v-model="keyword" placeholder="搜索配置、用户、租户..." @keyup.enter="resetPageAndLoad" />
            <button type="button" @click="resetPageAndLoad">查询</button>
          </label>
          <select v-model="isEnableCreate" @change="resetPageAndLoad">
            <option value="">全部开机状态</option>
            <option value="1">执行开机任务</option>
            <option value="0">无开机任务</option>
          </select>
        </div>
      </header>
      <p v-if="error" class="wd-error-line">{{ error }}</p>
      <p v-else-if="notice" class="wd-muted-line">{{ notice }}</p>
      <table class="wd-table">
        <thead>
          <tr>
            <th><input type="checkbox" :checked="rows.length > 0 && selectedCount === rows.length" @change="toggleAll(($event.target as HTMLInputElement).checked)" /></th>
            <th v-for="column in columns" :key="column.key">{{ column.label }}</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="columns.length + 2">加载中...</td>
          </tr>
          <tr v-else-if="rows.length === 0">
            <td :colspan="columns.length + 2">暂无配置数据</td>
          </tr>
          <tr v-for="(row, index) in rows" v-else :key="String(row.id || index)">
            <td><input type="checkbox" :checked="selectedIds.includes(rowId(row))" @change="toggleRow(row)" /></td>
            <td v-for="column in columns" :key="column.key">
              <span v-if="column.key === 'enableCreate'" class="wd-badge" :class="statusClass(row)">{{ cell(row, column.key) }}</span>
              <span v-else>{{ cell(row, column.key) }}</span>
            </td>
            <td>
              <div class="wd-row-actions">
                <button type="button" @click="openDetails(row)"><ExternalLink :size="14" />实时资源</button>
                <button type="button" @click="renameConfig(row)">改名</button>
                <button type="button" @click="releaseSecurityRule(row)"><ShieldCheck :size="14" />放行</button>
                <button type="button" :disabled="Number(row.enableCreate || 0) === 0" @click="stopCreate(row)">停止</button>
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
        <h2>OCI 实时操作台 · {{ displayName(selectedDetail) }}</h2>
        <div class="wd-actions compact">
          <button type="button" class="ghost" :disabled="detailLoading" @click="loadDetails(true)">
            <RefreshCw :size="16" />刷新 OCI
          </button>
          <button type="button" class="ghost" @click="selectedDetail = null">关闭</button>
        </div>
      </header>

      <div class="wd-detail-summary">
        <div><span>用户 OCID</span><strong>{{ detail?.userId || '-' }}</strong></div>
        <div><span>租户 OCID</span><strong>{{ detail?.tenantId || '-' }}</strong></div>
        <div><span>区域</span><strong>{{ detail?.region || selectedDetail.region || '-' }}</strong></div>
        <div><span>指纹</span><strong>{{ detail?.fingerprint || '-' }}</strong></div>
      </div>

      <p v-if="detailLoading" class="wd-muted-line">正在读取 OCI 实时资源...</p>
      <p v-else-if="detailInstances.length === 0" class="wd-muted-line">当前配置暂无实例，或 OCI API 未返回实例。</p>

      <div class="wd-instance-grid">
        <article v-for="instance in detailInstances" :key="instanceId(instance)" class="wd-instance-panel">
          <div class="wd-instance-head">
            <div>
              <h3>{{ instanceLabel(instance) }}</h3>
              <span>{{ instance.ocId }}</span>
            </div>
            <em class="wd-badge" :class="stateClass(instance.state)">{{ instance.state || 'UNKNOWN' }}</em>
          </div>

          <div class="wd-instance-meta">
            <div><Cpu :size="15" /><span>{{ instance.shape || '-' }}</span></div>
            <div><Zap :size="15" /><span>{{ instance.ocpus || '-' }} OCPU / {{ instance.memory || '-' }} GB</span></div>
            <div><HardDrive :size="15" /><span>{{ instance.bootVolumeSize || '-' }} GB</span></div>
            <div><Network :size="15" /><span>{{ publicIps(instance) }}</span></div>
          </div>

          <div class="wd-instance-actions">
            <button type="button" :disabled="isBusy('START', instanceId(instance))" @click="updateInstanceState(instance, 'START')"><Play :size="14" />启动</button>
            <button type="button" :disabled="isBusy('STOP', instanceId(instance))" @click="updateInstanceState(instance, 'STOP')"><Square :size="14" />停止</button>
            <button type="button" :disabled="isBusy('RESET', instanceId(instance))" @click="updateInstanceState(instance, 'RESET')"><RotateCcw :size="14" />重启</button>
            <button type="button" class="ghost" @click="renameInstance(instance)">改名</button>
            <button type="button" class="ghost" @click="getInstanceCfg(instance)">配置详情</button>
            <button type="button" class="ghost" @click="updateInstanceCfg(instance)">CPU/内存</button>
            <button type="button" class="ghost" @click="updateShape(instance)">Shape</button>
            <button type="button" class="ghost" @click="updateBootVolume(instance)">引导卷</button>
            <button type="button" class="ghost" @click="changeIp(instance)">换 IP</button>
            <button type="button" class="ghost" :disabled="Number(instance.enableChangeIp || 0) === 0" @click="stopChangeIp(instance)">停换 IP</button>
            <button type="button" class="ghost" @click="createIpv6(instance)">IPv6</button>
            <button type="button" class="ghost" @click="startVnc(instance)"><Terminal :size="14" />VNC</button>
            <button type="button" class="ghost" @click="autoRescue(instance)">救援</button>
            <button type="button" class="ghost" @click="enable500M(instance)">500M</button>
            <button type="button" class="ghost" @click="close500M(instance)">关 500M</button>
            <button type="button" class="danger-soft" @click="terminateInstance(instance)"><Power :size="14" />终止</button>
          </div>
        </article>
      </div>

      <div v-if="selectedInstanceCfg" class="wd-config-preview">
        <strong>最近读取的实例配置</strong>
        <pre>{{ JSON.stringify(selectedInstanceCfg, null, 2) }}</pre>
      </div>

      <div class="wd-live-side">
        <section>
          <h3>Cloudflare 配置</h3>
          <p v-if="detailCfCfgs.length === 0">暂无 Cloudflare 配置。</p>
          <ul v-else>
            <li v-for="cf in detailCfCfgs" :key="cf.cfCfgId">{{ cf.domain || cf.cfCfgId }}</li>
          </ul>
        </section>
        <section>
          <h3>网络负载均衡</h3>
          <p v-if="detailNlbs.length === 0">暂无 NLB。</p>
          <ul v-else>
            <li v-for="nlb in detailNlbs" :key="`${nlb.name}-${nlb.publicIp}`">{{ nlb.name }} · {{ nlb.status }} · {{ nlb.publicIp || '-' }}</li>
          </ul>
        </section>
      </div>
    </div>

    <div v-if="actionDialog" class="wd-dialog-backdrop" @click.self="closeActionDialog">
      <form class="wd-dialog" :class="{ danger: actionDialog.danger }" @submit.prevent="submitActionDialog">
        <header>
          <div>
            <span>{{ actionDialog.danger ? '高危操作确认' : '操作确认' }}</span>
            <h3>{{ actionDialog.title }}</h3>
          </div>
          <button type="button" class="ghost" @click="closeActionDialog">关闭</button>
        </header>

        <p>{{ actionDialog.description }}</p>

        <div v-if="actionDialog.target" class="wd-dialog-target">
          <span>目标</span>
          <strong>{{ actionDialog.target }}</strong>
        </div>

        <div v-if="actionDialog.fields.length" class="wd-dialog-fields">
          <label v-for="field in actionDialog.fields" :key="field.key" :class="{ checkbox: field.type === 'checkbox' }">
            <template v-if="field.type === 'checkbox'">
              <input :checked="Boolean(field.value)" type="checkbox" @change="updateDialogCheckbox(field, $event)" />
              <span>{{ field.label }}</span>
            </template>
            <template v-else>
              <span>{{ field.label }}</span>
              <textarea
                v-if="field.type === 'textarea'"
                :value="dialogFieldText(field)"
                :placeholder="field.placeholder"
                @input="updateDialogField(field, $event)"
              ></textarea>
              <input
                v-else
                :value="dialogFieldText(field)"
                :type="field.type || 'text'"
                :min="field.min"
                :placeholder="field.placeholder"
                @input="updateDialogField(field, $event)"
              />
            </template>
            <small v-if="field.help">{{ field.help }}</small>
          </label>
        </div>

        <footer>
          <button type="button" class="ghost" @click="closeActionDialog">取消</button>
          <button type="submit" :class="{ danger: actionDialog.danger }" :disabled="Boolean(actionBusy)">
            {{ actionBusy ? '提交中...' : actionDialog.actionLabel }}
          </button>
        </footer>
      </form>
    </div>
  </section>
</template>
