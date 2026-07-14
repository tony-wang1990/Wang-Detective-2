# Windows、Android 与 VPS 统一客户端

Wang-Detective 现在采用“一套 VPS 后端，三个客户端入口”的结构：Web、Windows Electron 和 Android Capacitor 共用现有 Vue 控制台，并访问同一个 Spring Boot API 与 WebSocket 服务。OCI 配置、任务、主机、审计、备份等业务数据只保存在 VPS，客户端不维护独立业务数据库，因此无需额外的数据同步服务。

## 目录结构

```text
frontend/              三端共用的 Vue 应用
apps/desktop/          Windows Electron 容器
apps/android/          Android Capacitor 容器
deploy/downloads/      VPS 对外发布 APK/EXE 的目录
```

原生客户端首次登录时填写 VPS 根地址，例如 `https://detective.example.com`。地址保存在当前设备，API 自动使用该域名下的 `/api`，日志、终端和监控 WebSocket 也使用同一域名。

## 功能覆盖

- “全部功能”通过受管理员 Token 保护的客户端网关复用 Spring 中注册的全部 Telegram 回调处理器，菜单、分页、确认操作和会话状态不另写第二套业务逻辑。
- Telegram 会话式流程已转换为原生客户端交互：VNC URL 和加密备份密码可直接输入，恢复流程可上传 ZIP 并显式确认覆盖，账号导入进入完整配置表单。
- “资源工具”补齐引导卷、Cloudflare 配置与 DNS、IP 数据、租户 IAM 安全操作和 OCI 流量查询等原有 REST 模块。
- Android 使用 Capacitor Filesystem、Share 和 Browser 插件处理备份、日志、审计 CSV、SFTP 文件、二维码附件与外部链接。
- Web、Windows 和 Android 使用同一 Vue 路由、同一 API 和同一 VPS 数据库；功能入口相同，原生层只处理窗口、文件和系统链接能力。

## Web 构建

```bash
npm ci --prefix frontend
npm --prefix frontend run build
```

默认产物写入 `src/main/resources/dist`，随 Spring Boot 应用发布。

## Windows 客户端

```bash
cd apps/desktop
npm install
npm run dev
npm run dist:win
```

安装包位于 `apps/desktop/release`。也可执行 `npm run dist:portable` 构建便携版。发布安装版：

```bash
node scripts/publish-client-package.mjs windows apps/desktop/release/Wang-Detective-Setup-0.1.2.exe
```

## Android 客户端

准备 JDK 17、Android Studio 和 Android SDK，然后执行：

```bash
cd apps/android
npm install
npm run open
npm run build:apk
```

首次执行会生成 `apps/android/android` 原生工程。调试 APK 位于 `apps/android/android/app/build/outputs/apk/debug/app-debug.apk`。正式发布前应在 Android Studio 中配置签名并执行 `npm run build:release`。发布 APK：

构建脚本会读取 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，并自动检查 Windows 常用的 `%LOCALAPPDATA%\Android\Sdk` 与 `C:\Android\Sdk`。

```bash
node scripts/publish-client-package.mjs android apps/android/android/app/build/outputs/apk/debug/app-debug.apk
```

## VPS 发布

`docker-compose.yml` 已将本机 `deploy` 映射到容器 `/app/king-detective/deploy`。将固定名称的安装包及其 `.sha256` 文件放进 `deploy/downloads` 后，控制台的“客户端下载”页面会自动显示版本、大小、更新时间、校验值和下载按钮。

默认从 GitHub 最新 Release 同步已验收的安装包：

```bash
cd /app/king-detective
bash scripts/sync-client-packages.sh
```

一键安装、手动更新和 watcher 更新都会调用该脚本。VPS 本地包缺失时，后端还会将下载链接回退到公开 GitHub Release 资产。

可用环境变量：

```env
CLIENT_DOWNLOAD_DIR=/app/king-detective/deploy/downloads
KING_DETECTIVE_CLIENT_VERSION=0.1.2
```

生产环境必须使用 HTTPS，并让反向代理转发 WebSocket 升级头及 `X-Forwarded-Proto`、`X-Forwarded-Host`。Android 若连接纯 HTTP 地址，虽然调试配置允许明文请求，生产发布仍建议强制 HTTPS。

## 数据一致性边界

- 所有业务写入立即落到同一个 VPS 后端，三端刷新后看到相同结果。
- 登录 Token 和服务器地址仅保存在各自客户端，不属于共享业务数据。
- 当前没有离线写入队列；客户端离线时不能修改 VPS 数据，恢复网络后重新读取最新状态。
- 同一账号可多端登录，权限、MFA、黑名单和审计策略均由 VPS 后端统一执行。
