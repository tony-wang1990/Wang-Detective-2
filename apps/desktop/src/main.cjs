const { app, BrowserWindow, Menu, dialog, shell } = require('electron');
const fs = require('node:fs');
const path = require('node:path');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 940,
    minWidth: 1100,
    minHeight: 720,
    title: 'Wang Detective',
    icon: path.join(__dirname, '..', 'build', 'icon.ico'),
    backgroundColor: '#0f172a',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  const indexFile = path.join(__dirname, '..', 'web', 'index.html');
  if (fs.existsSync(indexFile)) {
    mainWindow.loadFile(indexFile);
  } else {
    mainWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(missingBuildHtml())}`);
  }
}

function missingBuildHtml() {
  return `
    <body style="margin:0;background:#0f172a;color:#e5e7eb;font-family:Segoe UI,Arial,sans-serif;display:grid;place-items:center;min-height:100vh;">
      <main style="max-width:560px;padding:32px;line-height:1.7;">
        <h1 style="margin:0 0 12px;font-size:26px;">Wang Detective</h1>
        <p>桌面端前端资源还没有构建。请先在 apps/desktop 执行 <code>npm run prepare:web</code>，再启动客户端。</p>
      </main>
    </body>
  `;
}

function createMenu() {
  return Menu.buildFromTemplate([
    {
      label: 'Wang Detective',
      submenu: [
        { label: '重新加载', accelerator: 'CmdOrCtrl+R', click: () => mainWindow?.reload() },
        { label: '开发者工具', accelerator: 'F12', click: () => mainWindow?.webContents.openDevTools({ mode: 'detach' }) },
        { type: 'separator' },
        { label: '退出', role: 'quit' }
      ]
    }
  ]);
}

app.whenReady().then(() => {
  app.setAppUserModelId('com.tony.wangdetective');
  Menu.setApplicationMenu(createMenu());
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

process.on('uncaughtException', (error) => {
  dialog.showErrorBox('Wang Detective', error.stack || error.message);
});
