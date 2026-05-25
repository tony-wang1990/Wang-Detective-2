<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { LineChart, PieChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components';
import * as echarts from 'echarts/core';
import type { EChartsType } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Activity, CalendarDays, Globe2, Server, Zap } from 'lucide-vue-next';
import { apiGet, getHealth, type GlanceData, type HealthData } from '../api/http';

const glance = reactive<GlanceData>({});
const health = ref<HealthData>({});
const refreshedAt = ref('');
const nowText = ref('');
const mapRef = ref<HTMLElement | null>(null);
const cpuChartRef = ref<HTMLElement | null>(null);
const memoryChartRef = ref<HTMLElement | null>(null);
const trafficChartRef = ref<HTMLElement | null>(null);
const metricsStatus = ref('等待连接');
const refreshing = ref(false);
const displayVersion = computed(() => health.value.version || localStorage.getItem('currentVersion') || 'main');
let map: any = null;
let cityLayer: any = null;
let cpuChart: EChartsType | null = null;
let memoryChart: EChartsType | null = null;
let trafficChart: EChartsType | null = null;
let metricsWs: WebSocket | null = null;
let clockTimer: number | undefined;

echarts.use([PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer]);

const stats = [
  { label: '总 API 数量', key: 'users', icon: Server },
  { label: '开机任务数量', key: 'tasks', icon: Zap },
  { label: '区域数量', key: 'regions', icon: Globe2 },
  { label: '运行天数', key: 'days', icon: CalendarDays }
] as const;

const liveUptimeText = computed(() => {
  const seconds = Number(health.value.uptimeSeconds || 0);
  if (!seconds) return '-';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  if (days > 0) return `${days} 天 ${hours} 小时`;
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`;
  return `${minutes} 分 ${secs} 秒`;
});

type MetricPair = {
  used?: string | number;
  free?: string | number;
};

type MetricsMessage = {
  cpuUsage?: MetricPair;
  memoryUsage?: MetricPair;
  trafficData?: {
    timestamps?: string[];
    inbound?: number[];
    outbound?: number[];
  };
};

function formatBytes(value?: number) {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = value;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(index ? 1 : 0)} ${units[index]}`;
}

function percentValue(value?: string | number) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? Number(parsed.toFixed(2)) : 0;
}

function chartTextColor() {
  return document.documentElement.classList.contains('dark') ? '#cbd5e1' : '#64748b';
}

function initMap() {
  if (!mapRef.value || map) return;
  map = L.map(mapRef.value, {
    zoomControl: true,
    attributionControl: true,
    worldCopyJump: true
  }).setView([34, 108], 3);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map);
  cityLayer = L.layerGroup().addTo(map);
}

function markerHtml(count?: number) {
  return `<span>${count || 1}</span>`;
}

function renderMap() {
  if (!map) return;
  cityLayer?.clearLayers();
  const cities = (glance.cities || []).filter((city) => city.lat != null && city.lng != null);
  const bounds: Array<[number, number]> = [];
  for (const city of cities) {
    const latLng: [number, number] = [Number(city.lat), Number(city.lng)];
    bounds.push(latLng);
    const marker = L.marker(latLng, {
      icon: L.divIcon({
        className: 'wd-map-marker',
        html: markerHtml(city.count),
        iconSize: [34, 34],
        iconAnchor: [17, 17]
      })
    }).bindPopup(`
      <strong>${city.city || city.area || city.country || '未知区域'}</strong><br>
      ${city.country || ''} ${city.area || ''}<br>
      IP 数量：${city.count || 1}<br>
      ${city.org || ''}
    `);
    cityLayer.addLayer(marker);
  }
  if (bounds.length === 1) {
    map.setView(bounds[0], 5);
  } else if (bounds.length > 1) {
    map.fitBounds(bounds, { padding: [28, 28] });
  } else {
    map.setView([34, 108], 3);
  }
  setTimeout(() => map?.invalidateSize(), 100);
}

function initCharts() {
  if (cpuChartRef.value && !cpuChart) {
    cpuChart = echarts.init(cpuChartRef.value);
  }
  if (memoryChartRef.value && !memoryChart) {
    memoryChart = echarts.init(memoryChartRef.value);
  }
  if (trafficChartRef.value && !trafficChart) {
    trafficChart = echarts.init(trafficChartRef.value);
  }
  updateCharts({});
}

function pieOption(title: string, used: number, free: number) {
  return {
    title: {
      text: title,
      left: 'center',
      top: 4,
      textStyle: { color: chartTextColor(), fontSize: 14, fontWeight: 700 }
    },
    tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '58%'],
        label: { color: chartTextColor(), formatter: '{b}\n{d}%' },
        data: [
          { name: '已使用', value: used },
          { name: '空闲', value: Math.max(free, 0) }
        ],
        color: ['#14b8a6', '#dbeafe']
      }
    ]
  };
}

function updateCharts(metrics: MetricsMessage) {
  const memoryUsedFromHealth = health.value.maxMemoryBytes
    ? ((health.value.usedMemoryBytes || 0) / health.value.maxMemoryBytes) * 100
    : 0;
  const cpuUsed = percentValue(metrics.cpuUsage?.used);
  const cpuFree = metrics.cpuUsage?.free != null ? percentValue(metrics.cpuUsage.free) : 100 - cpuUsed;
  const memoryUsed = metrics.memoryUsage?.used != null ? percentValue(metrics.memoryUsage.used) : percentValue(memoryUsedFromHealth);
  const memoryFree = metrics.memoryUsage?.free != null ? percentValue(metrics.memoryUsage.free) : 100 - memoryUsed;
  cpuChart?.setOption(pieOption('CPU 使用率', cpuUsed, cpuFree));
  memoryChart?.setOption(pieOption('内存使用率', memoryUsed, memoryFree));
  trafficChart?.setOption({
    title: {
      text: '流量实时速率 (KB/s)',
      left: 'center',
      top: 4,
      textStyle: { color: chartTextColor(), fontSize: 14, fontWeight: 700 }
    },
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['入站', '出站'],
      bottom: 0,
      textStyle: { color: chartTextColor() }
    },
    grid: { left: 44, right: 22, top: 52, bottom: 44 },
    xAxis: {
      type: 'category',
      data: metrics.trafficData?.timestamps || [],
      axisLabel: { color: chartTextColor() }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: chartTextColor() },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, .18)' } }
    },
    series: [
      { name: '入站', type: 'line', smooth: true, showSymbol: false, data: metrics.trafficData?.inbound || [], color: '#14b8a6' },
      { name: '出站', type: 'line', smooth: true, showSymbol: false, data: metrics.trafficData?.outbound || [], color: '#f59e0b' }
    ]
  });
}

function tickClock() {
  nowText.value = new Date().toLocaleTimeString();
  if (health.value.uptimeSeconds != null) {
    health.value.uptimeSeconds += 1;
  }
}

function resizeVisuals() {
  map?.invalidateSize();
  cpuChart?.resize();
  memoryChart?.resize();
  trafficChart?.resize();
}

function disconnectMetrics() {
  if (metricsWs) {
    metricsWs.close();
    metricsWs = null;
  }
}

function connectMetrics() {
  const token = sessionStorage.getItem('token');
  if (!token) {
    metricsStatus.value = '缺少登录 token';
    return;
  }
  disconnectMetrics();
  const url = `${window.location.origin.replace(/^http/, 'ws')}/metrics/${encodeURIComponent(token)}`;
  metricsWs = new WebSocket(url);
  metricsStatus.value = '连接中';
  metricsWs.onopen = () => {
    metricsStatus.value = '实时推送中';
  };
  metricsWs.onmessage = (event) => {
    try {
      updateCharts(JSON.parse(String(event.data || '{}')) as MetricsMessage);
    } catch {
      metricsStatus.value = '指标解析失败';
    }
  };
  metricsWs.onerror = () => {
    metricsStatus.value = '连接异常';
  };
  metricsWs.onclose = () => {
    metricsStatus.value = '已断开';
  };
}

async function refresh() {
  refreshing.value = true;
  try {
    const [glanceResult, healthResult] = await Promise.all([
      apiGet<GlanceData>('/sys/glance').catch(() => null),
      getHealth().catch(() => ({} as HealthData))
    ]);
    if (glanceResult?.success) {
      Object.assign(glance, glanceResult.data || {});
      if (glanceResult.data?.currentVersion) {
        localStorage.setItem('currentVersion', glanceResult.data.currentVersion);
      }
    }
    health.value = healthResult;
    refreshedAt.value = new Date().toLocaleTimeString();
    renderMap();
    updateCharts({});
  } finally {
    refreshing.value = false;
  }
}

onMounted(async () => {
  await nextTick();
  tickClock();
  clockTimer = window.setInterval(tickClock, 1000);
  initMap();
  initCharts();
  await refresh();
  connectMetrics();
  window.addEventListener('resize', resizeVisuals);
});

onBeforeUnmount(() => {
  if (clockTimer) {
    window.clearInterval(clockTimer);
  }
  disconnectMetrics();
  window.removeEventListener('resize', resizeVisuals);
  cpuChart?.dispose();
  memoryChart?.dispose();
  trafficChart?.dispose();
  map?.remove();
  cpuChart = null;
  memoryChart = null;
  trafficChart = null;
  map = null;
});
</script>

<template>
  <div class="wd-page">
    <section class="wd-kpis">
      <article v-for="item in stats" :key="item.key" class="wd-kpi">
        <component :is="item.icon" :size="24" />
        <div>
          <span>{{ item.label }}</span>
          <strong>{{ glance[item.key] ?? 0 }}</strong>
        </div>
      </article>
    </section>

    <section class="wd-home-grid">
      <article class="wd-card wd-map">
        <header>
          <h2>资源分布地图</h2>
          <button type="button" :disabled="refreshing" @click="refresh">{{ refreshing ? '刷新中' : '刷新' }}</button>
        </header>
        <div class="wd-map-body">
          <div ref="mapRef" class="wd-leaflet-map"></div>
          <div v-if="!glance.cities?.length" class="wd-map-empty">暂无 IP 地理数据</div>
        </div>
      </article>

      <article class="wd-card">
        <header>
          <h2>系统诊断</h2>
          <span>{{ nowText || refreshedAt || '未刷新' }}</span>
        </header>
        <div class="wd-health-list">
          <div>
            <Activity :size="18" />
            <b>API 网关</b>
            <em>{{ health.status || 'UNKNOWN' }}</em>
          </div>
          <div>
            <Activity :size="18" />
            <b>数据库连接</b>
            <em>{{ health.databaseConnectivity ? '正常' : '待检查' }}</em>
          </div>
          <div>
            <Activity :size="18" />
            <b>内存使用</b>
            <em>{{ formatBytes(health.usedMemoryBytes) }} / {{ formatBytes(health.maxMemoryBytes) }}</em>
          </div>
          <div>
            <Activity :size="18" />
            <b>运行版本</b>
            <em>{{ displayVersion }}</em>
          </div>
          <div>
            <Activity :size="18" />
            <b>运行时长</b>
            <em>{{ liveUptimeText }}</em>
          </div>
        </div>
      </article>
    </section>

    <section class="wd-charts-grid">
      <article class="wd-card">
        <header>
          <h2>资源使用情况</h2>
          <span>{{ metricsStatus }}</span>
        </header>
        <div class="wd-chart-row">
          <div ref="cpuChartRef" class="wd-chart"></div>
          <div ref="memoryChartRef" class="wd-chart"></div>
        </div>
      </article>

      <article class="wd-card">
        <header>
          <h2>网络流量</h2>
          <button type="button" :disabled="metricsStatus === '连接中'" @click="connectMetrics">
            {{ metricsStatus === '连接中' ? '连接中' : '重连指标' }}
          </button>
        </header>
        <div ref="trafficChartRef" class="wd-chart wide"></div>
      </article>
    </section>
  </div>
</template>
