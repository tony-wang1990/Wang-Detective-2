(function () {
  const icons = {
    features: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9L12 3z"/></svg>',
    terminal: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/><path d="M7 9l3 3-3 3"/><path d="M12 15h5"/></svg>'
  };

  const entries = [
    {
      id: 'wang-feature-center-entry',
      label: '功能中心',
      icon: icons.features,
      href: '/dashboard/features',
      title: '功能中心'
    },
    {
      id: 'wang-ops-terminal-entry',
      label: '运维终端',
      icon: icons.terminal,
      href: '/dashboard/ops-terminal',
      title: '运维终端'
    }
  ];

  function buildEntry(entry) {
    const item = document.createElement('li');
    item.id = entry.id;
    item.className = 'el-menu-item wang-extra-menu-item';
    item.setAttribute('role', 'menuitem');
    item.style.cssText = [
      'height:56px',
      'line-height:56px',
      'display:grid',
      'grid-template-columns:28px minmax(0,1fr)',
      'align-items:center',
      'gap:12px',
      'padding:0 14px',
      'cursor:pointer'
    ].join(';');
    item.innerHTML = [
      '<span class="wang-extra-icon" aria-hidden="true">',
      entry.icon,
      '</span>',
      '<span class="menu-text">',
      entry.label,
      '</span>'
    ].join('');
    item.addEventListener('click', function (event) {
      event.preventDefault();
      window.location.href = entry.href;
    });
    return item;
  }

  function currentTheme() {
    return document.documentElement.classList.contains('dark') || localStorage.getItem('theme') === 'dark'
      ? 'dark'
      : 'light';
  }

  function embeddedUrl(href) {
    const url = new URL(href, window.location.origin);
    url.searchParams.set('embedded', '1');
    url.searchParams.set('theme', currentTheme());
    return url.pathname + url.search + url.hash;
  }

  function syncEmbeddedTheme(iframe) {
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

  function setMenuActive(activeId) {
    document.querySelectorAll('.sidebar-menu .el-menu-item').forEach(function (item) {
      item.classList.toggle('is-active', item.id === activeId);
    });
  }

  function restoreMainContent() {
    const main = document.querySelector('.el-main');
    if (!main) {
      return;
    }
    const panel = document.getElementById('wang-embedded-panel');
    if (panel) {
      panel.remove();
    }
    Array.from(main.children).forEach(function (child) {
      child.style.display = '';
    });
  }

  function renderEmbeddedPage(entry) {
    const main = document.querySelector('.el-main');
    if (!main) {
      window.location.href = entry.href;
      return;
    }

    Array.from(main.children).forEach(function (child) {
      if (child.id !== 'wang-embedded-panel') {
        child.style.display = 'none';
      }
    });

    let panel = document.getElementById('wang-embedded-panel');
    if (!panel) {
      panel = document.createElement('div');
      panel.id = 'wang-embedded-panel';
      main.appendChild(panel);
    }

    const isDark = currentTheme() === 'dark';
    panel.style.cssText = [
      'display:block',
      'height:calc(100vh - 112px)',
      'min-height:680px',
      'background:' + (isDark ? '#0b1220' : '#f6f8fb'),
      'border-radius:8px',
      'overflow:hidden'
    ].join(';');

    panel.innerHTML = [
      '<iframe id="wang-embedded-frame" title="',
      entry.title,
      '" src="',
      embeddedUrl(entry.href),
      '" style="width:100%;height:100%;border:0;display:block;background:',
      isDark ? '#0b1220' : '#f6f8fb',
      '"></iframe>'
    ].join('');

    const iframe = document.getElementById('wang-embedded-frame');
    iframe.addEventListener('load', function () {
      try {
        const token = sessionStorage.getItem('token');
        if (token) {
          iframe.contentWindow.sessionStorage.setItem('token', token);
        }
        syncEmbeddedTheme(iframe);
      } catch (error) {
        console.warn('Failed to sync embedded session:', error.message);
      }
    });
    syncEmbeddedTheme(iframe);

    setMenuActive(entry.id);
  }

  function injectFeatureEntries() {
    const menu = document.querySelector('.sidebar-menu');
    if (!menu || document.getElementById(entries[0].id)) {
      return;
    }

    entries.forEach(function (entry) {
      menu.appendChild(buildEntry(entry));
    });

    if (!document.getElementById('wang-feature-entry-style')) {
      const style = document.createElement('style');
      style.id = 'wang-feature-entry-style';
      style.textContent = [
        '.wang-extra-menu-item{color:#e8eefc!important;}',
        '.wang-extra-menu-item:hover{background:rgba(8,145,178,.16)!important;color:#fff!important;}',
        '.wang-extra-icon{width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;color:#67e8f9;}',
        '.wang-extra-icon svg{width:19px;height:19px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;}',
        '.sidebar.collapsed .wang-extra-menu-item{justify-content:center;padding:0!important;}',
        '.sidebar.collapsed .wang-extra-menu-item .menu-text{display:none;}',
        '.wang-extra-menu-item.is-active{background:rgba(8,145,178,.26)!important;color:#fff!important;}'
      ].join('');
      document.head.appendChild(style);
    }

    menu.addEventListener('click', function (event) {
      if (!event.target.closest('.wang-extra-menu-item')) {
        restoreMainContent();
      }
    }, true);
  }

  function applyVersionInfo(versionInfo) {
    const currentVersion = versionInfo.currentVersion || 'dev';
    const latestVersion = versionInfo.latestVersion || currentVersion;
    localStorage.setItem('currentVersion', currentVersion);
    localStorage.setItem('latestVersion', latestVersion);

    document.querySelectorAll('button').forEach(function (button) {
      const text = button.textContent || '';
      if (!text.includes('新版本:')) {
        return;
      }
      if (currentVersion === latestVersion) {
        button.style.display = 'none';
      } else {
        button.style.display = '';
        button.textContent = '🔔 新版本:' + latestVersion;
      }
    });

    document.querySelectorAll('footer a').forEach(function (link) {
      if ((link.textContent || '').includes('Tony Wang')) {
        link.textContent = '© Tony Wang All Rights Reserved ' + currentVersion;
      }
    });
  }

  async function refreshVersionInfo() {
    const token = sessionStorage.getItem('token');
    if (!token) {
      return;
    }
    try {
      const response = await fetch('/api/v1/system/version-info', {
        headers: { Authorization: 'Bearer ' + token }
      });
      const json = await response.json();
      if (json && json.data) {
        applyVersionInfo(json.data);
      }
    } catch (error) {
      console.warn('Failed to refresh Wang-Detective version info:', error.message);
    }
  }

  const observer = new MutationObserver(injectFeatureEntries);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  const themeObserver = new MutationObserver(function () {
    syncEmbeddedTheme(document.getElementById('wang-embedded-frame'));
  });
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
  window.addEventListener('storage', function (event) {
    if (event.key === 'theme') {
      syncEmbeddedTheme(document.getElementById('wang-embedded-frame'));
    }
  });
  window.addEventListener('load', injectFeatureEntries);
  window.addEventListener('load', refreshVersionInfo);
  document.addEventListener('DOMContentLoaded', injectFeatureEntries);
  document.addEventListener('DOMContentLoaded', refreshVersionInfo);
  setTimeout(injectFeatureEntries, 1000);
  setTimeout(refreshVersionInfo, 1500);
})();
