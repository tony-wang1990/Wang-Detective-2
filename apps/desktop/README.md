# Wang Detective Desktop

Windows 桌面客户端使用 Electron 打包，内部加载同一份 Vue 控制台。首次登录时填写 VPS 控制台地址，例如 `https://example.com`，客户端会把所有 API、WebSocket 和下载请求指向同一台 VPS。

## Commands

```bash
npm install
npm run dev
npm run dist:win
npm run dist:portable
```

安装版和便携版输出到 `release`。发布安装版时，在项目根目录执行：

```bash
node scripts/publish-client-package.mjs windows apps/desktop/release/Wang-Detective-Setup-0.1.0.exe
```

脚本会复制为 `deploy/downloads/Wang-Detective-Setup-latest.exe` 并生成 SHA256，控制台的“客户端下载中心”会自动显示可下载状态。
