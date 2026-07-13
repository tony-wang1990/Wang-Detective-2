<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  ChartNoAxesCombined,
  Cloud,
  DatabaseZap,
  HardDrive,
  MapPin,
  Plus,
  RefreshCw,
  Save,
  Search,
  Trash2,
  Users
} from 'lucide-vue-next';
import { apiPost, apiPostLong, notifyGlobal, type PageResult } from '../api/http';

type TabId = 'boot' | 'cloudflare' | 'ip' | 'tenant' | 'traffic';
type OciConfig = { id: string; username?: string; tenantName?: string; region?: string; regionName?: string };
type BootVolume = { id: string; displayName?: string; instanceName?: string; availabilityDomain?: string; sizeInGBs?: string; vpusPerGB?: string; lifecycleState?: string; attached?: boolean; timeCreated?: string };
type CfConfig = { id: string; domain?: string; zoneId?: string; apiToken?: string; createTime?: string };
type DnsRecord = { id: string; name?: string; type?: string; content?: string; proxied?: boolean; ttl?: number; comment?: string; modifiedOn?: string };
type IpRecord = { id: string; ip?: string; country?: string; area?: string; city?: string; org?: string; asn?: string; createTime?: string };
type TenantUser = { id: string; name?: string; email?: string; lifecycleState?: string; emailVerified?: boolean; isMfaActivated?: boolean; lastSuccessfulLoginTime?: string };
type TenantInfo = { id?: string; name?: string; description?: string; homeRegionKey?: string; regions?: string[]; userList?: TenantUser[]; passwordExpiresAfter?: number };
type ValueLabel = { value: string; label: string };
type TrafficCondition = { regionOptions?: ValueLabel[]; instanceOptions?: Record<string, ValueLabel[]> };
type TrafficData = { time?: string[]; inbound?: string[]; outbound?: string[] };
type TrafficSummary = { inboundTraffic?: string; outboundTraffic?: string; instanceCount?: number };

const tabs = [
  { id: 'boot' as const, label: '引导卷', icon: HardDrive },
  { id: 'cloudflare' as const, label: 'Cloudflare', icon: Cloud },
  { id: 'ip' as const, label: 'IP 数据', icon: MapPin },
  { id: 'tenant' as const, label: '租户安全', icon: Users },
  { id: 'traffic' as const, label: '流量查询', icon: ChartNoAxesCombined }
];

const activeTab = ref<TabId>('boot');
const configs = ref<OciConfig[]>([]);
const loading = ref(false);
const error = ref('');
const confirmState = reactive({ open: false, title: '', message: '', label: '确认执行' });
let confirmResolver: ((confirmed: boolean) => void) | undefined;

const bootCfgId = ref('');
const bootKeyword = ref('');
const bootVolumes = ref<BootVolume[]>([]);
const bootEdit = reactive({ id: '', size: '', vpu: '' });

const cfConfigs = ref<CfConfig[]>([]);
const selectedCfId = ref('');
const cfForm = reactive({ id: '', domain: '', zoneId: '', apiToken: '' });
const dnsRecords = ref<DnsRecord[]>([]);
const dnsForm = reactive({ id: '', prefix: '', type: 'A', ipAddress: '', proxied: false, ttl: 1, comment: '' });

const ipKeyword = ref('');
const ipAddress = ref('');
const ipRecords = ref<IpRecord[]>([]);

const tenantCfgId = ref('');
const tenantRegion = ref('');
const tenant = ref<TenantInfo>();
const tenantEdit = reactive({ userId: '', email: '', dbUserName: '', description: '' });
const passwordExpiresAfter = ref(120);

const trafficCfgId = ref('');
const trafficCondition = ref<TrafficCondition>();
const trafficRegion = ref('');
const trafficInstanceId = ref('');
const trafficVnicId = ref('');
const trafficVnics = ref<ValueLabel[]>([]);
const trafficSummary = ref<TrafficSummary>();
const trafficData = ref<TrafficData>();
const trafficBegin = ref(toLocalInput(new Date(Date.now() - 24 * 60 * 60 * 1000)));
const trafficEnd = ref(toLocalInput(new Date()));

const selectedCf = computed(() => cfConfigs.value.find((item) => item.id === selectedCfId.value));
const trafficInstances = computed(() => trafficCondition.value?.instanceOptions?.[trafficRegion.value] || []);
const trafficRows = computed(() => (trafficData.value?.time || []).map((time, index) => ({
  time,
  inbound: trafficData.value?.inbound?.[index] || '-',
  outbound: trafficData.value?.outbound?.[index] || '-'
})));

function toLocalInput(date: Date) {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function configLabel(item: OciConfig) {
  return item.username || item.tenantName || item.id;
}

function maskToken(value?: string) {
  if (!value) return '-';
  return value.length < 10 ? '••••••••' : `${value.slice(0, 4)}••••${value.slice(-4)}`;
}

function askForConfirmation(title: string, message: string, label = '确认执行') {
  confirmState.title = title;
  confirmState.message = message;
  confirmState.label = label;
  confirmState.open = true;
  return new Promise<boolean>((resolve) => {
    confirmResolver = resolve;
  });
}

function resolveConfirmation(confirmed: boolean) {
  confirmState.open = false;
  confirmResolver?.(confirmed);
  confirmResolver = undefined;
}

async function run(action: () => Promise<void>, fallback: string) {
  if (loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    await action();
  } catch (err) {
    error.value = err instanceof Error ? err.message : fallback;
    notifyGlobal(error.value, 'error');
  } finally {
    loading.value = false;
  }
}

async function loadOciConfigs() {
  const response = await apiPost<PageResult<OciConfig>>('/oci/userPage', { currentPage: 1, pageSize: 1000, keyword: '' });
  configs.value = response.data?.records || [];
  const first = configs.value[0];
  if (first) {
    bootCfgId.value ||= first.id;
    tenantCfgId.value ||= first.id;
    tenantRegion.value ||= first.region || '';
    trafficCfgId.value ||= first.id;
  }
}

async function refreshOciConfigs() {
  await run(loadOciConfigs, '刷新 OCI 配置失败');
}

async function loadBootVolumes() {
  await run(async () => {
    if (!bootCfgId.value) throw new Error('请先选择 OCI 配置');
    const response = await apiPostLong<PageResult<BootVolume>>('/bootVolume/page', {
      ociCfgId: bootCfgId.value,
      keyword: bootKeyword.value,
      currentPage: 1,
      pageSize: 200,
      cleanReLaunch: true
    });
    bootVolumes.value = response.data?.records || [];
  }, '读取引导卷失败');
}

function startBootEdit(item: BootVolume) {
  bootEdit.id = item.id;
  bootEdit.size = item.sizeInGBs || '';
  bootEdit.vpu = item.vpusPerGB || '';
}

async function updateBootVolume() {
  if (!bootEdit.id || !bootEdit.size || !bootEdit.vpu) return;
  await run(async () => {
    await apiPost('/bootVolume/update', {
      ociCfgId: bootCfgId.value,
      bootVolumeId: bootEdit.id,
      bootVolumeSize: bootEdit.size,
      bootVolumeVpu: bootEdit.vpu
    });
    notifyGlobal('引导卷配置已更新', 'success');
    bootEdit.id = '';
    await loadBootVolumesDirect();
  }, '更新引导卷失败');
}

async function loadBootVolumesDirect() {
  const response = await apiPostLong<PageResult<BootVolume>>('/bootVolume/page', {
    ociCfgId: bootCfgId.value,
    keyword: bootKeyword.value,
    currentPage: 1,
    pageSize: 200,
    cleanReLaunch: true
  });
  bootVolumes.value = response.data?.records || [];
}

async function terminateBootVolume(item: BootVolume) {
  if (!await askForConfirmation('终止引导卷', `将终止“${item.displayName || item.id}”，此操作不可恢复。`, '确认终止')) return;
  await run(async () => {
    await apiPost('/bootVolume/terminate', { ociCfgId: bootCfgId.value, bootVolumeIds: [item.id] });
    notifyGlobal('引导卷终止命令已下发', 'success');
    await loadBootVolumesDirect();
  }, '终止引导卷失败');
}

async function loadCfConfigsDirect() {
  const response = await apiPost<PageResult<CfConfig>>('/cf/listCfg', { currentPage: 1, pageSize: 200, keyword: '' });
  cfConfigs.value = response.data?.records || [];
  if (!selectedCfId.value && cfConfigs.value[0]) selectedCfId.value = cfConfigs.value[0].id;
}

async function loadCfConfigs() {
  await run(loadCfConfigsDirect, '读取 Cloudflare 配置失败');
}

function editCfConfig(item?: CfConfig) {
  cfForm.id = item?.id || '';
  cfForm.domain = item?.domain || '';
  cfForm.zoneId = item?.zoneId || '';
  cfForm.apiToken = item?.apiToken || '';
}

async function saveCfConfig() {
  await run(async () => {
    if (!cfForm.domain || !cfForm.zoneId || !cfForm.apiToken) throw new Error('域名、Zone ID 和 API Token 均不能为空');
    await apiPost(cfForm.id ? '/cf/update' : '/cf/add', { ...cfForm });
    notifyGlobal(cfForm.id ? 'Cloudflare 配置已更新' : 'Cloudflare 配置已添加', 'success');
    editCfConfig();
    await loadCfConfigsDirect();
  }, '保存 Cloudflare 配置失败');
}

async function removeCfConfig(item: CfConfig) {
  if (!await askForConfirmation('删除 Cloudflare 配置', `将删除“${item.domain || item.id}”及其本地配置引用。`, '确认删除')) return;
  await run(async () => {
    await apiPost('/cf/removeBatch', { idList: [item.id] });
    if (selectedCfId.value === item.id) selectedCfId.value = '';
    await loadCfConfigsDirect();
    dnsRecords.value = [];
    notifyGlobal('Cloudflare 配置已删除', 'success');
  }, '删除 Cloudflare 配置失败');
}

async function loadDnsDirect() {
  if (!selectedCfId.value) throw new Error('请先选择 Cloudflare 配置');
  const response = await apiPostLong<PageResult<DnsRecord>>('/cf/listCfDnsRecord', {
    cfCfgId: selectedCfId.value,
    currentPage: 1,
    pageSize: 200,
    keyword: '',
    cleanReLaunch: true
  });
  dnsRecords.value = response.data?.records || [];
}

async function loadDns() {
  await run(loadDnsDirect, '读取 DNS 记录失败');
}

function editDns(item?: DnsRecord) {
  const domain = selectedCf.value?.domain || '';
  const fullName = item?.name || '';
  dnsForm.id = item?.id || '';
  dnsForm.prefix = domain && (fullName === domain || fullName.endsWith(`.${domain}`))
    ? fullName.slice(0, Math.max(0, fullName.length - domain.length)).replace(/\.$/, '')
    : fullName;
  dnsForm.type = item?.type || 'A';
  dnsForm.ipAddress = item?.content || '';
  dnsForm.proxied = Boolean(item?.proxied);
  dnsForm.ttl = item?.ttl || 1;
  dnsForm.comment = item?.comment || '';
}

async function saveDns() {
  await run(async () => {
    if (!selectedCfId.value || !dnsForm.type || !dnsForm.ipAddress) throw new Error('配置、记录类型和地址不能为空');
    await apiPost(dnsForm.id ? '/cf/updateCfDnsRecord' : '/cf/addCfDnsRecord', {
      cfCfgId: selectedCfId.value,
      id: dnsForm.id || undefined,
      prefix: dnsForm.prefix,
      type: dnsForm.type,
      ipAddress: dnsForm.ipAddress,
      proxied: dnsForm.proxied,
      ttl: dnsForm.ttl,
      comment: dnsForm.comment
    });
    editDns();
    await loadDnsDirect();
    notifyGlobal('DNS 记录已保存', 'success');
  }, '保存 DNS 记录失败');
}

async function removeDns(item: DnsRecord) {
  if (!await askForConfirmation('删除 DNS 记录', `将从 Cloudflare 删除“${item.name || item.id}”。`, '确认删除')) return;
  await run(async () => {
    await apiPost('/cf/removeCfDnsRecord', { cfCfgId: selectedCfId.value, recordIds: [item.id] });
    await loadDnsDirect();
    notifyGlobal('DNS 记录已删除', 'success');
  }, '删除 DNS 记录失败');
}

async function loadIpDirect() {
  const response = await apiPost<PageResult<IpRecord>>('/ipData/page', { currentPage: 1, pageSize: 500, keyword: ipKeyword.value });
  ipRecords.value = response.data?.records || [];
}

async function loadIpData() {
  await run(loadIpDirect, '读取 IP 数据失败');
}

async function addIpData() {
  if (!ipAddress.value.trim()) return;
  await run(async () => {
    await apiPost('/ipData/add', { ip: ipAddress.value.trim() });
    ipAddress.value = '';
    await loadIpDirect();
    notifyGlobal('IP 已加入数据库', 'success');
  }, '添加 IP 失败');
}

async function refreshIp(item?: IpRecord) {
  await run(async () => {
    if (item) await apiPost('/ipData/update', { id: item.id });
    else await apiPostLong('/ipData/loadOciIpData', {});
    await loadIpDirect();
    notifyGlobal(item ? 'IP 归属信息已刷新' : 'OCI IP 数据已同步', 'success');
  }, '刷新 IP 数据失败');
}

async function removeIp(item: IpRecord) {
  if (!await askForConfirmation('删除 IP 数据', `将删除“${item.ip || item.id}”的归属记录。`, '确认删除')) return;
  await run(async () => {
    await apiPost('/ipData/remove', { idList: [item.id] });
    await loadIpDirect();
  }, '删除 IP 失败');
}

async function loadTenantDirect() {
  if (!tenantCfgId.value) throw new Error('请先选择 OCI 配置');
  const response = await apiPostLong<TenantInfo>('/tenant/tenantInfo', {
    ociCfgId: tenantCfgId.value,
    region: tenantRegion.value,
    cleanReLaunch: true
  });
  tenant.value = response.data;
  passwordExpiresAfter.value = response.data?.passwordExpiresAfter ?? 120;
  tenantRegion.value ||= response.data?.homeRegionKey || response.data?.regions?.[0] || '';
}

async function loadTenant() {
  await run(loadTenantDirect, '读取租户信息失败');
}

function editTenantUser(user?: TenantUser) {
  tenantEdit.userId = user?.id || '';
  tenantEdit.email = user?.email || '';
  tenantEdit.dbUserName = user?.name || '';
  tenantEdit.description = '';
}

async function saveTenantUser() {
  if (!tenantEdit.userId || !tenantEdit.email || !tenantEdit.dbUserName) return;
  await run(async () => {
    await apiPost('/tenant/updateUserInfo', { ociCfgId: tenantCfgId.value, ...tenantEdit });
    editTenantUser();
    await loadTenantDirect();
    notifyGlobal('租户用户资料已更新', 'success');
  }, '更新租户用户失败');
}

async function tenantUserAction(endpoint: string, user: TenantUser, label: string) {
  if (!await askForConfirmation(`${label} IAM 用户`, `目标用户：“${user.name || user.id}”。请确认这是预期操作。`, `确认${label}`)) return;
  await run(async () => {
    await apiPost(`/tenant/${endpoint}`, { ociCfgId: tenantCfgId.value, userId: user.id });
    await loadTenantDirect();
    notifyGlobal(`${label}操作已完成`, 'success');
  }, `${label}失败`);
}

async function updatePasswordPolicy() {
  await run(async () => {
    await apiPost('/tenant/updatePwdEx', { cfgId: tenantCfgId.value, passwordExpiresAfter: passwordExpiresAfter.value });
    notifyGlobal('密码过期策略已更新', 'success');
    await loadTenantDirect();
  }, '更新密码策略失败');
}

async function loadTrafficConditionDirect() {
  if (!trafficCfgId.value) throw new Error('请先选择 OCI 配置');
  const response = await apiPostLong<TrafficCondition>(`/traffic/getCondition?ociCfgId=${encodeURIComponent(trafficCfgId.value)}`, {});
  trafficCondition.value = response.data;
  trafficRegion.value = response.data?.regionOptions?.[0]?.value || '';
  trafficInstanceId.value = '';
  trafficVnicId.value = '';
  trafficVnics.value = [];
  trafficData.value = undefined;
}

async function loadTrafficCondition() {
  await run(loadTrafficConditionDirect, '读取流量查询条件失败');
}

async function loadTrafficSummary() {
  if (!trafficCfgId.value || !trafficRegion.value) return;
  await run(async () => {
    const response = await apiPostLong<TrafficSummary>(`/traffic/fetchInstances?ociCfgId=${encodeURIComponent(trafficCfgId.value)}&region=${encodeURIComponent(trafficRegion.value)}`, {});
    trafficSummary.value = response.data;
  }, '读取区域流量汇总失败');
}

async function loadTrafficVnics() {
  if (!trafficInstanceId.value) return;
  await run(async () => {
    const response = await apiPostLong<ValueLabel[]>(`/traffic/fetchVnics?ociCfgId=${encodeURIComponent(trafficCfgId.value)}&region=${encodeURIComponent(trafficRegion.value)}&instanceId=${encodeURIComponent(trafficInstanceId.value)}`, {});
    trafficVnics.value = response.data || [];
    trafficVnicId.value = trafficVnics.value[0]?.value || '';
  }, '读取 VNIC 失败');
}

async function loadTrafficData() {
  await run(async () => {
    if (!trafficCfgId.value || !trafficRegion.value || !trafficVnicId.value) throw new Error('请选择配置、区域、实例和 VNIC');
    const id = trafficVnicId.value.replace(/"/g, '');
    const response = await apiPostLong<TrafficData>('/traffic/data', {
      ociCfgId: trafficCfgId.value,
      beginTime: new Date(trafficBegin.value).toISOString(),
      endTime: new Date(trafficEnd.value).toISOString(),
      region: trafficRegion.value,
      inQuery: `VnicToNetworkBytes[60m]{resourceId = "${id}"}.sum()`,
      outQuery: `VnicFromNetworkBytes[60m]{resourceId = "${id}"}.sum()`,
      namespace: 'oci_vcn'
    });
    trafficData.value = response.data;
  }, '读取流量明细失败');
}

onMounted(async () => {
  await run(async () => {
    await Promise.all([loadOciConfigs(), loadCfConfigsDirect(), loadIpDirect()]);
  }, '初始化资源工具失败');
});
</script>

<template>
  <section class="wd-page infrastructure-tools">
    <header class="wd-page-title">
      <div>
        <h1>资源工具</h1>
        <p>管理 OCI 引导卷、Cloudflare DNS、IP 数据、租户安全和流量指标。</p>
      </div>
      <div class="wd-actions compact">
        <button type="button" :disabled="loading" @click="refreshOciConfigs">
          <RefreshCw :size="16" :class="{ spinning: loading }" />刷新配置
        </button>
      </div>
    </header>

    <div class="infra-tabs" role="tablist">
      <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">
        <component :is="tab.icon" :size="16" />{{ tab.label }}
      </button>
    </div>
    <div v-if="error" class="wd-notice danger">{{ error }}</div>

    <section v-if="activeTab === 'boot'" class="infra-panel">
      <header><div><h2>引导卷管理</h2><p>查询、扩容、调整 VPU 或终止独立引导卷。</p></div></header>
      <div class="infra-toolbar">
        <select v-model="bootCfgId"><option value="">选择 OCI 配置</option><option v-for="item in configs" :key="item.id" :value="item.id">{{ configLabel(item) }}</option></select>
        <input v-model="bootKeyword" placeholder="名称或 OCID" @keyup.enter="loadBootVolumes" />
        <button type="button" :disabled="loading" @click="loadBootVolumes"><Search :size="16" />查询</button>
      </div>
      <form v-if="bootEdit.id" class="infra-edit-band" @submit.prevent="updateBootVolume">
        <strong>调整引导卷</strong><input v-model="bootEdit.size" required placeholder="容量 GB" /><input v-model="bootEdit.vpu" required placeholder="VPU/GB" />
        <button type="submit"><Save :size="15" />保存</button><button type="button" class="ghost" @click="bootEdit.id = ''">取消</button>
      </form>
      <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>名称</th><th>实例</th><th>容量</th><th>VPU</th><th>状态</th><th>可用域</th><th>操作</th></tr></thead><tbody>
        <tr v-for="item in bootVolumes" :key="item.id"><td><strong>{{ item.displayName || '-' }}</strong><small>{{ item.id }}</small></td><td>{{ item.instanceName || '未挂载' }}</td><td>{{ item.sizeInGBs }} GB</td><td>{{ item.vpusPerGB }}</td><td>{{ item.lifecycleState }}</td><td>{{ item.availabilityDomain }}</td><td class="row-actions"><button type="button" class="ghost" @click="startBootEdit(item)">调整</button><button type="button" class="danger-soft" @click="terminateBootVolume(item)"><Trash2 :size="14" />终止</button></td></tr>
        <tr v-if="!bootVolumes.length"><td colspan="7" class="wd-empty">选择配置后查询引导卷。</td></tr>
      </tbody></table></div>
    </section>

    <section v-else-if="activeTab === 'cloudflare'" class="infra-panel">
      <header><div><h2>Cloudflare 与 DNS</h2><p>配置 Zone Token，并管理对应的 DNS 记录。</p></div><button type="button" class="ghost" @click="editCfConfig()"><Plus :size="15" />新配置</button></header>
      <form class="infra-form-grid" @submit.prevent="saveCfConfig">
        <label>域名<input v-model="cfForm.domain" required placeholder="example.com" /></label><label>Zone ID<input v-model="cfForm.zoneId" required /></label><label>API Token<input v-model="cfForm.apiToken" required type="password" /></label>
        <div class="form-actions"><button type="submit"><Save :size="15" />{{ cfForm.id ? '更新配置' : '添加配置' }}</button><button v-if="cfForm.id" type="button" class="ghost" @click="editCfConfig()">取消编辑</button></div>
      </form>
      <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>域名</th><th>Zone ID</th><th>Token</th><th>创建时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="item in cfConfigs" :key="item.id" :class="{ selected: selectedCfId === item.id }" @click="selectedCfId = item.id"><td>{{ item.domain }}</td><td>{{ item.zoneId }}</td><td>{{ maskToken(item.apiToken) }}</td><td>{{ item.createTime }}</td><td class="row-actions"><button type="button" class="ghost" @click.stop="editCfConfig(item)">编辑</button><button type="button" class="danger-soft" @click.stop="removeCfConfig(item)"><Trash2 :size="14" />删除</button></td></tr>
      </tbody></table></div>
      <div class="infra-subsection">
        <header><div><h3>DNS 记录 {{ selectedCf?.domain ? `· ${selectedCf.domain}` : '' }}</h3></div><button type="button" :disabled="!selectedCfId || loading" @click="loadDns"><RefreshCw :size="15" />读取记录</button></header>
        <form class="infra-form-grid dns" @submit.prevent="saveDns"><label>前缀<input v-model="dnsForm.prefix" placeholder="www 或留空" /></label><label>类型<select v-model="dnsForm.type"><option>A</option><option>AAAA</option><option>CNAME</option><option>TXT</option></select></label><label>内容 / IP<input v-model="dnsForm.ipAddress" required /></label><label>TTL<input v-model.number="dnsForm.ttl" type="number" min="1" /></label><label class="check"><input v-model="dnsForm.proxied" type="checkbox" />启用代理</label><label>备注<input v-model="dnsForm.comment" /></label><div class="form-actions"><button type="submit" :disabled="!selectedCfId"><Save :size="15" />{{ dnsForm.id ? '更新记录' : '添加记录' }}</button><button v-if="dnsForm.id" type="button" class="ghost" @click="editDns()">取消编辑</button></div></form>
        <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>名称</th><th>类型</th><th>内容</th><th>代理</th><th>TTL</th><th>操作</th></tr></thead><tbody><tr v-for="item in dnsRecords" :key="item.id"><td>{{ item.name }}</td><td>{{ item.type }}</td><td>{{ item.content }}</td><td>{{ item.proxied ? '开启' : '关闭' }}</td><td>{{ item.ttl }}</td><td class="row-actions"><button type="button" class="ghost" @click="editDns(item)">编辑</button><button type="button" class="danger-soft" @click="removeDns(item)"><Trash2 :size="14" />删除</button></td></tr><tr v-if="!dnsRecords.length"><td colspan="6" class="wd-empty">选择配置并读取 DNS 记录。</td></tr></tbody></table></div>
      </div>
    </section>

    <section v-else-if="activeTab === 'ip'" class="infra-panel">
      <header><div><h2>IP 数据库</h2><p>维护地址归属、城市、运营商与坐标数据。</p></div><button type="button" :disabled="loading" @click="refreshIp()"><DatabaseZap :size="16" />同步 OCI IP</button></header>
      <div class="infra-toolbar"><input v-model="ipAddress" placeholder="添加 IP 地址" @keyup.enter="addIpData" /><button type="button" @click="addIpData"><Plus :size="15" />添加</button><input v-model="ipKeyword" placeholder="筛选 IP / 地区 / 运营商" @keyup.enter="loadIpData" /><button type="button" class="ghost" @click="loadIpData"><Search :size="15" />查询</button></div>
      <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>IP</th><th>国家/地区</th><th>城市</th><th>运营商</th><th>ASN</th><th>操作</th></tr></thead><tbody><tr v-for="item in ipRecords" :key="item.id"><td>{{ item.ip }}</td><td>{{ [item.country, item.area].filter(Boolean).join(' / ') || '-' }}</td><td>{{ item.city || '-' }}</td><td>{{ item.org || '-' }}</td><td>{{ item.asn || '-' }}</td><td class="row-actions"><button type="button" class="ghost" @click="refreshIp(item)"><RefreshCw :size="14" />刷新</button><button type="button" class="danger-soft" @click="removeIp(item)"><Trash2 :size="14" />删除</button></td></tr><tr v-if="!ipRecords.length"><td colspan="6" class="wd-empty">暂无 IP 数据。</td></tr></tbody></table></div>
    </section>

    <section v-else-if="activeTab === 'tenant'" class="infra-panel">
      <header><div><h2>租户与用户安全</h2><p>管理 IAM 用户、MFA、API 密钥、密码和过期策略。</p></div></header>
      <div class="infra-toolbar"><select v-model="tenantCfgId" @change="tenantRegion = configs.find(item => item.id === tenantCfgId)?.region || ''"><option value="">选择 OCI 配置</option><option v-for="item in configs" :key="item.id" :value="item.id">{{ configLabel(item) }}</option></select><input v-model="tenantRegion" placeholder="区域，例如 ap-singapore-1" /><button type="button" @click="loadTenant"><Search :size="15" />读取租户</button></div>
      <div v-if="tenant" class="tenant-summary"><div><span>租户</span><strong>{{ tenant.name }}</strong><small>{{ tenant.id }}</small></div><div><span>主区域</span><strong>{{ tenant.homeRegionKey }}</strong></div><div><span>IAM 用户</span><strong>{{ tenant.userList?.length || 0 }}</strong></div><form @submit.prevent="updatePasswordPolicy"><label>密码过期天数<input v-model.number="passwordExpiresAfter" type="number" min="0" /></label><button type="submit"><Save :size="14" />保存策略</button></form></div>
      <form v-if="tenantEdit.userId" class="infra-edit-band tenant-edit" @submit.prevent="saveTenantUser"><strong>编辑 IAM 用户</strong><input v-model="tenantEdit.dbUserName" required placeholder="用户名" /><input v-model="tenantEdit.email" required type="email" placeholder="邮箱" /><input v-model="tenantEdit.description" placeholder="描述" /><button type="submit"><Save :size="15" />保存</button><button type="button" class="ghost" @click="editTenantUser()">取消</button></form>
      <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>用户</th><th>邮箱</th><th>状态</th><th>MFA</th><th>最近登录</th><th>操作</th></tr></thead><tbody><tr v-for="user in tenant?.userList || []" :key="user.id"><td><strong>{{ user.name }}</strong><small>{{ user.id }}</small></td><td>{{ user.email || '-' }}</td><td>{{ user.lifecycleState }}</td><td>{{ user.isMfaActivated ? '已启用' : '未启用' }}</td><td>{{ user.lastSuccessfulLoginTime || '-' }}</td><td class="row-actions wrap"><button type="button" class="ghost" @click="editTenantUser(user)">编辑</button><button type="button" class="ghost" @click="tenantUserAction('deleteMfaDevice', user, '清除 MFA')">清 MFA</button><button type="button" class="ghost" @click="tenantUserAction('deleteApiKey', user, '清除 API 密钥')">清 API</button><button type="button" class="ghost" @click="tenantUserAction('resetPassword', user, '重置密码')">重置密码</button><button type="button" class="danger-soft" @click="tenantUserAction('deleteUser', user, '删除')"><Trash2 :size="14" />删除</button></td></tr><tr v-if="!tenant?.userList?.length"><td colspan="6" class="wd-empty">选择配置并读取租户信息。</td></tr></tbody></table></div>
    </section>

    <section v-else class="infra-panel">
      <header><div><h2>OCI 流量查询</h2><p>按区域、实例和 VNIC 查询流入、流出指标。</p></div></header>
      <div class="infra-form-grid traffic"><label>OCI 配置<select v-model="trafficCfgId"><option value="">选择配置</option><option v-for="item in configs" :key="item.id" :value="item.id">{{ configLabel(item) }}</option></select></label><div class="form-actions"><button type="button" @click="loadTrafficCondition"><RefreshCw :size="15" />读取区域与实例</button></div><label>区域<select v-model="trafficRegion" @change="trafficInstanceId = ''; trafficVnicId = ''; trafficVnics = []"><option value="">选择区域</option><option v-for="item in trafficCondition?.regionOptions || []" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label>实例<select v-model="trafficInstanceId" @change="loadTrafficVnics"><option value="">选择实例</option><option v-for="item in trafficInstances" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label>VNIC<select v-model="trafficVnicId"><option value="">选择 VNIC</option><option v-for="item in trafficVnics" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label>开始时间<input v-model="trafficBegin" type="datetime-local" /></label><label>结束时间<input v-model="trafficEnd" type="datetime-local" /></label><div class="form-actions"><button type="button" @click="loadTrafficData"><Search :size="15" />查询明细</button><button type="button" class="ghost" @click="loadTrafficSummary">区域汇总</button></div></div>
      <div v-if="trafficSummary" class="traffic-summary"><div><span>实例数</span><strong>{{ trafficSummary.instanceCount || 0 }}</strong></div><div><span>本月流入</span><strong>{{ trafficSummary.inboundTraffic || '-' }}</strong></div><div><span>本月流出</span><strong>{{ trafficSummary.outboundTraffic || '-' }}</strong></div></div>
      <div class="wd-table-scroll"><table class="wd-table compact"><thead><tr><th>时间</th><th>流入 GB</th><th>流出 GB</th></tr></thead><tbody><tr v-for="item in trafficRows" :key="item.time"><td>{{ item.time }}</td><td>{{ item.inbound }}</td><td>{{ item.outbound }}</td></tr><tr v-if="!trafficRows.length"><td colspan="3" class="wd-empty">选择 VNIC 与时间范围后查询流量。</td></tr></tbody></table></div>
    </section>

    <div v-if="confirmState.open" class="wd-dialog-backdrop" @click.self="resolveConfirmation(false)">
      <form class="wd-dialog danger" @submit.prevent="resolveConfirmation(true)">
        <header><div><span>高风险操作确认</span><h3>{{ confirmState.title }}</h3></div><button type="button" class="ghost" @click="resolveConfirmation(false)">关闭</button></header>
        <p>{{ confirmState.message }}</p>
        <footer><button type="button" class="ghost" @click="resolveConfirmation(false)">取消</button><button type="submit" class="danger">{{ confirmState.label }}</button></footer>
      </form>
    </div>
  </section>
</template>

<style scoped>
.infrastructure-tools { gap: 14px; }
.infra-tabs { display: flex; gap: 4px; overflow-x: auto; padding: 4px; border: 1px solid var(--wd-line); background: var(--wd-surface); }
.infra-tabs button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-width: 124px; padding: 9px 12px; border: 0; background: transparent; color: var(--wd-muted); }
.infra-tabs button.active { background: var(--wd-accent); color: #fff; }
.infra-panel { border: 1px solid var(--wd-line); background: var(--wd-surface); }
.infra-panel > header, .infra-subsection > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 18px; border-bottom: 1px solid var(--wd-line); }
.infra-panel h2, .infra-panel h3 { margin: 0; letter-spacing: 0; }
.infra-panel h2 { font-size: 18px; }.infra-panel h3 { font-size: 16px; }
.infra-panel header p { margin: 5px 0 0; color: var(--wd-muted); font-size: 13px; }
.infra-panel button, .infra-toolbar button, .infra-form-grid button, .infra-edit-band button, .tenant-summary button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; padding: 8px 11px; border: 1px solid var(--wd-accent); background: var(--wd-accent); color: #fff; }
.infra-panel button.ghost { border-color: var(--wd-line); background: var(--wd-surface-2); color: var(--wd-text); }
.infra-panel button.danger-soft { border-color: color-mix(in srgb, var(--wd-danger) 38%, var(--wd-line)); background: transparent; color: var(--wd-danger); }
.infra-toolbar { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto; gap: 9px; padding: 14px 18px; border-bottom: 1px solid var(--wd-line); }
.infra-toolbar input, .infra-toolbar select, .infra-form-grid input, .infra-form-grid select, .infra-edit-band input, .tenant-summary input { min-height: 38px; width: 100%; padding: 7px 9px; border: 1px solid var(--wd-line); background: var(--wd-surface); color: var(--wd-text); }
.infra-form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-items: end; gap: 11px; padding: 14px 18px; border-bottom: 1px solid var(--wd-line); }
.infra-form-grid label, .tenant-summary label { display: grid; gap: 5px; color: var(--wd-muted); font-size: 12px; }
.infra-form-grid .check { display: flex; align-items: center; gap: 8px; min-height: 38px; }.infra-form-grid .check input { width: auto; min-height: auto; }
.form-actions { display: flex; flex-wrap: wrap; gap: 7px; align-items: center; }
.infra-edit-band { display: flex; align-items: center; gap: 9px; padding: 12px 18px; border-bottom: 1px solid var(--wd-line); background: color-mix(in srgb, var(--wd-surface) 92%, var(--wd-accent) 8%); }
.infra-edit-band input { max-width: 220px; }
.infra-subsection { border-top: 8px solid var(--wd-bg); }
.wd-table td small { display: block; max-width: 320px; margin-top: 3px; overflow: hidden; color: var(--wd-muted); font-size: 10px; text-overflow: ellipsis; }
.row-actions { display: flex; gap: 6px; }.row-actions.wrap { flex-wrap: wrap; }
.row-actions button { min-height: 30px; padding: 5px 8px; font-size: 12px; }
.tenant-summary, .traffic-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; border-bottom: 1px solid var(--wd-line); background: var(--wd-line); }
.tenant-summary > div, .traffic-summary > div, .tenant-summary form { display: grid; gap: 4px; padding: 14px 16px; background: var(--wd-surface-2); }
.tenant-summary span, .traffic-summary span { color: var(--wd-muted); font-size: 12px; }.tenant-summary small { overflow: hidden; color: var(--wd-muted); text-overflow: ellipsis; }
.tenant-summary form { grid-template-columns: 1fr auto; align-items: end; }
.traffic-summary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 900px) { .infra-form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.tenant-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }.infra-toolbar { grid-template-columns: 1fr 1fr; }.row-actions { min-width: 220px; } }
@media (max-width: 620px) { .infra-form-grid, .infra-toolbar, .tenant-summary, .traffic-summary { grid-template-columns: 1fr; }.infra-edit-band { align-items: stretch; flex-direction: column; }.infra-edit-band input { max-width: none; }.infra-panel > header { align-items: flex-start; }.infra-tabs button { min-width: 108px; }.infra-form-grid .form-actions { grid-column: 1; } }
</style>
