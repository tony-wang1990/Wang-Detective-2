(function () {
  const REFRESH_MS = 30000;
  const MENU_ICONS = {
    home: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1v-8.5z"/></svg>',
    user: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
    task: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 11l2 2 4-5"/><path d="M20 12a8 8 0 1 1-5.3-7.5"/></svg>',
    log: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3h7l4 4v14H7z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h6"/></svg>',
    system: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"/><path d="M4.9 15.5l-1.1 2 2.3 2.3 2-1.1 1.3.5.6 2.3h4l.6-2.3 1.3-.5 2 1.1 2.3-2.3-1.1-2 .5-1.3 2.3-.6v-4l-2.3-.6-.5-1.3 1.1-2-2.3-2.3-2 1.1-1.3-.5L14 1.7h-4l-.6 2.3-1.3.5-2-1.1-2.3 2.3 1.1 2-.5 1.3-2.3.6v4l2.3.6z"/></svg>',
    ai: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 8h10a4 4 0 0 1 4 4v1a4 4 0 0 1-4 4h-4l-4 3v-3H7a4 4 0 0 1-4-4v-1a4 4 0 0 1 4-4z"/><path d="M9 12h.01M15 12h.01"/></svg>',
    features: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9L12 3z"/></svg>',
    terminal: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/><path d="M7 9l3 3-3 3"/><path d="M12 15h5"/></svg>'
  };
  const MENU_ORDER = ['home', 'user', 'task', 'log', 'system', 'ai'];
  const MENU_LABELS = {
    '主页': 'home',
    '配置列表': 'user',
    '任务列表': 'task',
    '服务日志': 'log',
    '系统配置': 'system',
    'AI聊天室': 'ai',
    '新版功能': 'features',
    '功能中心': 'features',
    '运维终端': 'terminal'
  };

  function token() {
    return sessionStorage.getItem('token') || '';
  }

  function currentTheme() {
    return document.documentElement.classList.contains('dark') || localStorage.getItem('theme') === 'dark'
      ? 'dark'
      : 'light';
  }

  function syncEmbeddedTheme() {
    const iframe = document.getElementById('wang-embedded-frame');
    if (!iframe || !iframe.contentWindow) {
      return;
    }
    const theme = currentTheme();
    try {
      iframe.contentWindow.localStorage.setItem('theme', theme);
      iframe.contentWindow.document.documentElement.classList.toggle('dark', theme === 'dark');
      iframe.contentWindow.postMessage({ type: 'wang-theme', theme }, window.location.origin);
    } catch (error) {
      console.warn('Failed to sync embedded theme:', error.message);
    }
  }

  function isDashboard() {
    return window.location.pathname.indexOf('/dashboard') === 0;
  }

  function isHomeDashboard() {
    const path = window.location.pathname;
    return path === '/dashboard' || path === '/dashboard/' || path === '/dashboard/home';
  }

  async function fetchJson(url) {
    const headers = {};
    const currentToken = token();
    if (currentToken) {
      headers.Authorization = 'Bearer ' + currentToken;
    }
    const response = await fetch(url, { headers });
    if (!response.ok) {
      throw new Error(url + ' ' + response.status);
    }
    return response.json();
  }

  function formatBytes(value) {
    const number = Number(value || 0);
    if (!number) {
      return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = number;
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
      size /= 1024;
      index += 1;
    }
    return size.toFixed(index === 0 ? 0 : 1) + ' ' + units[index];
  }

  function formatUptime(seconds) {
    const value = Number(seconds || 0);
    if (value < 60) {
      return value + ' 秒';
    }
    const minutes = Math.floor(value / 60);
    if (minutes < 60) {
      return minutes + ' 分钟';
    }
    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
      return hours + ' 小时';
    }
    return Math.floor(hours / 24) + ' 天';
  }

  function ensureLoginCopy() {
    if (window.location.pathname !== '/login') {
      return;
    }
    document.title = 'W-探长 登录';
    const button = document.querySelector('.login-button');
    if (button && button.textContent.trim() !== '登录控制台') {
      button.textContent = '登录控制台';
    }
    const title = document.querySelector('.login-title');
    if (title && title.textContent.trim() !== '登录控制台') {
      title.textContent = '登录控制台';
    }
    const subtitle = document.querySelector('.login-subtitle');
    if (subtitle && subtitle.textContent.trim() !== 'OCI 资源与运维管理') {
      subtitle.textContent = 'OCI 资源与运维管理';
    }
  }

  function ensureLoginRedirect() {
    const hasToken = Boolean(token());
    const loginVisible = Boolean(document.querySelector('.login-container'));
    const dashboardVisible = Boolean(document.querySelector('.dashboard-container'));

    if (hasToken && loginVisible && !dashboardVisible) {
      const target = '/dashboard/home';
      if (window.location.pathname !== target) {
        window.location.replace(target);
      } else if (!sessionStorage.getItem('wang_dashboard_reload_once')) {
        sessionStorage.setItem('wang_dashboard_reload_once', '1');
        window.location.reload();
      }
    }
  }

  function ensureTopbar() {
    if (!isDashboard()) {
      return;
    }
    const header = document.querySelector('.dashboard-container .header');
    if (!header || header.querySelector('.wang-topbar-left')) {
      return;
    }

    const left = document.createElement('div');
    left.className = 'wang-topbar-left';
    left.innerHTML = [
      '<button type="button" class="wang-menu-button" aria-label="menu">☰</button>',
      '<label class="wang-search-box">',
      '<span aria-hidden="true">⌕</span>',
      '<input readonly value="" placeholder="搜索资源、任务、日志等...">',
      '<kbd>⌘K</kbd>',
      '</label>',
      '<div class="wang-health-pill"><span></span><b>系统健康</b><em id="wangTopHealth">检测中</em></div>',
      '<div class="wang-version-pill"><b>版本</b><em id="wangTopVersion">main</em></div>'
    ].join('');

    header.insertBefore(left, header.firstChild);
    const menuButton = left.querySelector('.wang-menu-button');
    menuButton.addEventListener('click', function () {
      const toggle = document.querySelector('.dashboard-container .toggle-button');
      if (toggle) {
        toggle.click();
      }
    });
    refreshTopbar();
  }

  async function refreshTopbar() {
    if (!isDashboard()) {
      return;
    }
    const healthEl = document.getElementById('wangTopHealth');
    const versionEl = document.getElementById('wangTopVersion');
    try {
      const health = await fetchJson('/actuator/health');
      if (healthEl) {
        healthEl.textContent = health.status === 'UP' ? '正常' : '异常';
        healthEl.className = health.status === 'UP' ? 'ok' : 'warn';
      }
      if (versionEl) {
        versionEl.textContent = health.version || localStorage.getItem('currentVersion') || 'main';
      }
    } catch (error) {
      if (healthEl) {
        healthEl.textContent = '未知';
        healthEl.className = 'warn';
      }
    }
  }

  function ensureSidebarInfo() {
    if (!isDashboard()) {
      return;
    }
    const sidebar = document.querySelector('.dashboard-container .sidebar');
    if (!sidebar || sidebar.querySelector('.wang-sidebar-meta')) {
      return;
    }

    const card = document.createElement('div');
    card.className = 'wang-sidebar-meta';
    card.innerHTML = [
      '<div class="wang-meta-label">API 网关地址</div>',
      '<div class="wang-meta-host">',
      window.location.host,
      '</div>',
      '<div class="wang-meta-row"><span>环境</span><b>生产环境</b></div>',
      '<div class="wang-meta-row"><span>面板</span><b>W-探长</b></div>'
    ].join('');
    sidebar.appendChild(card);
  }

  function ensureMenuIcons() {
    const items = Array.from(document.querySelectorAll('.sidebar-menu .el-menu-item'));
    items.forEach(function (item, index) {
      const label = (item.textContent || '').trim();
      const key = MENU_LABELS[label] || (item.classList.contains('wang-extra-menu-item') ? null : MENU_ORDER[index]);
      const svg = MENU_ICONS[key];
      const icon = item.querySelector('.menu-icon, .wang-extra-icon, .el-icon');
      if (!svg || !icon || icon.classList.contains('wang-menu-icon-ready')) {
        return;
      }
      icon.innerHTML = svg;
      icon.classList.add('wang-menu-icon-ready');
    });
  }

  function makeDiagRow(title, detail, state) {
    const row = document.createElement('div');
    row.className = 'wang-diag-row ' + (state || 'ok');
    row.innerHTML = [
      '<span class="wang-diag-icon"></span>',
      '<div><b>',
      title,
      '</b><small>',
      detail || '',
      '</small></div>',
      '<em>',
      state === 'warn' ? '警告' : state === 'error' ? '异常' : '正常',
      '</em>'
    ].join('');
    return row;
  }

  function ensureDashboardGrid() {
    if (!isHomeDashboard()) {
      return;
    }
    const map = document.getElementById('map');
    if (!map || document.querySelector('.wang-map-grid')) {
      return;
    }

    const grid = document.createElement('section');
    grid.className = 'wang-map-grid';

    const mapCard = document.createElement('div');
    mapCard.className = 'wang-map-card';
    const mapHead = document.createElement('div');
    mapHead.className = 'wang-card-head';
    mapHead.innerHTML = '<h2>资源分布地图</h2><div><button type="button">全屏查看</button><button type="button" id="wangMapResize">刷新</button></div>';

    const diagCard = document.createElement('aside');
    diagCard.className = 'wang-diagnostics-card';
    diagCard.innerHTML = [
      '<div class="wang-card-head">',
      '<h2>系统诊断</h2>',
      '<span id="wangDiagTime">刷新中</span>',
      '</div>',
      '<div id="wangDiagRows" class="wang-diag-rows"></div>',
      '<a href="/dashboard/features" class="wang-diag-link">查看完整诊断报告 ›</a>'
    ].join('');

    map.parentNode.insertBefore(grid, map);
    mapCard.appendChild(mapHead);
    mapCard.appendChild(map);
    grid.appendChild(mapCard);
    grid.appendChild(diagCard);

    const resize = function () {
      window.dispatchEvent(new Event('resize'));
    };
    const resizeButton = document.getElementById('wangMapResize');
    if (resizeButton) {
      resizeButton.addEventListener('click', resize);
    }
    setTimeout(resize, 300);
    refreshDiagnostics();
  }

  async function refreshDiagnostics() {
    const rows = document.getElementById('wangDiagRows');
    const time = document.getElementById('wangDiagTime');
    if (!rows) {
      return;
    }

    try {
      const health = await fetchJson('/actuator/health');
      rows.innerHTML = '';
      rows.appendChild(makeDiagRow('API 网关连通性', window.location.origin + '/actuator/health', health.status === 'UP' ? 'ok' : 'error'));
      rows.appendChild(makeDiagRow('数据库连接', health.databaseConnectivity ? '健康检查通过' : '连接状态未知', health.databaseConnectivity ? 'ok' : 'warn'));
      rows.appendChild(makeDiagRow('内存使用', formatBytes(health.usedMemoryBytes) + ' / ' + formatBytes(health.maxMemoryBytes), health.memoryStatus ? 'ok' : 'warn'));
      rows.appendChild(makeDiagRow('运行版本', health.version || localStorage.getItem('currentVersion') || 'main', 'ok'));
      rows.appendChild(makeDiagRow('系统运行', formatUptime(health.uptimeSeconds), 'ok'));
      if (time) {
        time.textContent = new Date().toLocaleTimeString() + ' 刷新';
      }
    } catch (error) {
      rows.innerHTML = '';
      rows.appendChild(makeDiagRow('系统诊断', '暂时无法读取健康检查', 'warn'));
      if (time) {
        time.textContent = '读取失败';
      }
    }
  }

  function ensureChartTitle() {
    if (!isHomeDashboard()) {
      return;
    }
    const chartContainer = document.querySelector('.chart-container');
    if (!chartContainer || document.querySelector('.wang-chart-block')) {
      return;
    }
    const block = document.createElement('section');
    block.className = 'wang-chart-block';
    const head = document.createElement('div');
    head.className = 'wang-card-head';
    head.innerHTML = '<h2>资源使用情况</h2><span>数据每 30 秒自动刷新</span>';
    chartContainer.parentNode.insertBefore(block, chartContainer);
    block.appendChild(head);
    block.appendChild(chartContainer);
    setTimeout(function () {
      window.dispatchEvent(new Event('resize'));
    }, 300);
  }

  function apply() {
    ensureLoginRedirect();
    ensureLoginCopy();
    ensureTopbar();
    ensureMenuIcons();
    ensureSidebarInfo();
    syncEmbeddedTheme();
    ensureDashboardGrid();
    ensureChartTitle();
  }

  let scheduled = false;
  function scheduleApply() {
    if (scheduled) {
      return;
    }
    scheduled = true;
    window.requestAnimationFrame(function () {
      scheduled = false;
      apply();
    });
  }

  const observer = new MutationObserver(scheduleApply);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  const themeObserver = new MutationObserver(scheduleApply);
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
  window.addEventListener('storage', function (event) {
    if (event.key === 'theme') {
      scheduleApply();
    }
  });
  window.addEventListener('load', scheduleApply);
  document.addEventListener('DOMContentLoaded', scheduleApply);
  window.addEventListener('popstate', scheduleApply);
  setInterval(function () {
    refreshTopbar();
    refreshDiagnostics();
    scheduleApply();
  }, REFRESH_MS);
  setTimeout(scheduleApply, 500);
  setTimeout(scheduleApply, 1500);
})();
