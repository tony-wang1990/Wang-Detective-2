import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router';
import { hasServerBaseUrl, isNativeClient } from '../runtime/client';

const DashboardLayout = () => import('../layout/DashboardLayout.vue');
const LoginView = () => import('../views/LoginView.vue');
const HomeView = () => import('../views/HomeView.vue');
const FeatureCenterView = () => import('../views/FeatureCenterView.vue');
const ResourceListView = () => import('../views/ResourceListView.vue');
const TaskListView = () => import('../views/TaskListView.vue');
const ServiceLogView = () => import('../views/ServiceLogView.vue');
const OpsAuditView = () => import('../views/OpsAuditView.vue');
const SystemConfigView = () => import('../views/SystemConfigView.vue');
const OpsTerminalView = () => import('../views/OpsTerminalView.vue');
const RiskDashboardView = () => import('../views/RiskDashboardView.vue');
const BackupArchiveView = () => import('../views/BackupArchiveView.vue');
const RescueCenterView = () => import('../views/RescueCenterView.vue');
const ClientDownloadView = () => import('../views/ClientDownloadView.vue');
const FullFeatureView = () => import('../views/FullFeatureView.vue');
const InfrastructureToolsView = () => import('../views/InfrastructureToolsView.vue');

const router = createRouter({
  history: isNativeClient() ? createWebHashHistory() : createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    {
      path: '/dashboard',
      component: DashboardLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard/home' },
        { path: 'home', component: HomeView },
        { path: 'user', component: ResourceListView },
        { path: 'createTask', component: TaskListView },
        { path: 'risk', component: RiskDashboardView },
        { path: 'backups', component: BackupArchiveView },
        { path: 'rescue', component: RescueCenterView },
        { path: 'clients', component: ClientDownloadView },
        { path: 'all-features', component: FullFeatureView },
        { path: 'infrastructure', component: InfrastructureToolsView },
        { path: 'ociLog', component: ServiceLogView },
        { path: 'ops-audit', component: OpsAuditView },
        { path: 'sysCfg', component: SystemConfigView },
        { path: 'features', component: FeatureCenterView },
        { path: 'ops-terminal', component: OpsTerminalView },
        { path: ':legacyRoute(.*)*', component: FeatureCenterView, props: { mode: 'legacy' } }
      ]
    }
  ]
});

router.beforeEach((to) => {
  const hasToken = Boolean(sessionStorage.getItem('token'));
  if (isNativeClient() && !hasServerBaseUrl() && to.path !== '/login') {
    return '/login';
  }
  if (to.path === '/login' && hasToken && hasServerBaseUrl()) {
    return '/dashboard/home';
  }
  if (to.matched.some((record) => record.meta.requiresAuth) && !hasToken) {
    return '/login';
  }
  return true;
});

export default router;
