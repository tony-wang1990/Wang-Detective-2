# Wang Detective Android

Android 客户端使用 Capacitor 封装现有 Vue 控制台。客户端首次登录时填写 VPS 的 HTTPS 地址，之后所有 API 和 WebSocket 请求都指向该 VPS，与 Web、Windows 客户端共享数据。

## 环境

- Node.js 20+
- JDK 17
- Android Studio 和 Android SDK

## 开发与构建

```bash
npm install
npm run open
npm run build:apk
```

`npm run open` 会首次生成并打开 Android Studio 工程。调试 APK 输出到：

```text
apps/android/android/app/build/outputs/apk/debug/app-debug.apk
```

生产发布前请在 Android Studio 中配置正式签名，然后执行 `npm run build:release`。将最终 APK 复制为：

```text
deploy/downloads/wang-detective-latest.apk
```
