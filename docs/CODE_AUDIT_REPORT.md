# Wang-Detective 代码审计与优化报告

审计日期：2026-05-26

## 审计结论

本轮对后端 Controller、OCI Service、Vue 路由、前端 API 调用、WebSocket、Telegram Bot 菜单与回调处理做了第一轮发布前审计。结论是：核心链路已经可以作为阶段版本继续部署验证，现有 Vue 页面调用的 API 均能在后端找到对应接口；OCI 实例、租户、流量、VCN、引导卷、安全规则等核心数据仍由 OCI SDK 实时读取或在短期缓存后返回，不是纯静态假数据。

需要注意：首页总 API 数、任务数、区域数来自本地数据库统计；首页地图来自 `ip_data` 表，通常由 OCI IP 数据加载任务刷新；配置详情页本轮已改为调用 `/api/oci/details` 并强制刷新缓存，用于读取 OCI 实例和 NLB 实时详情。

## 本轮已完成核对

| 范围 | 结果 |
|---|---|
| Vue 路由 | `/dashboard/home`、`/dashboard/user`、`/dashboard/createTask`、`/dashboard/ociLog`、`/dashboard/sysCfg`、`/dashboard/ai-chat`、`/dashboard/features`、`/dashboard/ops-terminal`、`/dashboard/ops-audit`、`/dashboard/risk`、`/dashboard/backups`、`/dashboard/rescue` 均为原生 Vue 路由 |
| 前端 API 映射 | 当前 Vue 源码使用的 `/api/*` 调用均能匹配后端 Controller |
| WebSocket | 日志 `/logs`、SSH `/ops/ssh/terminal/{sessionId}`、指标 `/metrics/{token}` 均有后端处理器 |
| TGBOT 回调 | 静态扫描按钮回调，当前入口均有处理器或明确的 no-op 处理 |
| TGBOT 前缀冲突 | 已修复工厂按回调 pattern 长度优先匹配，避免 `traffic_history` 抢先吃掉 `traffic_history_query:` |
| OCI 实时调用 | `/api/oci/details`、实例/流量/租户/VCN/安全规则/引导卷相关接口仍通过 `OracleInstanceFetcher` 和 OCI SDK 执行 |

## 本轮修复与优化

1. 修复 Telegram Bot 回调前缀冲突风险：`CallbackHandlerFactory` 改为按 pattern 长度倒序匹配。
2. 修复 Telegram 分页中间按钮 `page_info` 无处理器的问题，归入 `NoopHandler`。
3. 修复账号选择辅助键盘的回调前缀，从错误的 `account:` 改为已有处理器识别的 `account_detail:`。
4. 修复创建实例流程返回按钮回调，从无处理器的 `create_instance:` 改为现有 `ci:` 流程。
5. 增强 TGBOT 运维中心：新增“操作审计”和“主机概览”入口，并同步加入快捷运维菜单。
6. 修复新版登录页 MFA 缺口：登录页会读取 `/api/sys/getEnableMfa`，启用 MFA 时显示验证码输入框并随登录提交。
7. 增强配置列表：新增 OCI 配置表单，调用 `/api/oci/addCfg` 上传 config 内容和私钥文件。
8. 增强配置详情：从本地行预览改为调用 `/api/oci/details`，并使用 `cleanReLaunchDetails: true` 强制刷新 OCI 实时详情。
9. 新增前端 `apiForm` 封装，统一 multipart 表单提交和 token 注入。
10. 清理本次构建产生的旧 Vue hash 资源，避免生产 dist 中堆积无引用 JS 文件。
11. 旧静态入口 `/ip-map.html`、`/wang-features.html`、`/ops-terminal.html` 已停止展示旧 UI 或模拟数据，统一跳转新版 Vue 路由。
12. 远程冒烟脚本补充旧入口迁移、VCN 和安全规则只读检查，用于部署后验证真实线上状态。

## 功能真实性说明

| 功能 | 数据来源 |
|---|---|
| 配置列表 | 本地 SQLite 的 OCI 配置表 |
| 配置实时详情 | OCI SDK 实时读取实例、VNIC、NLB，并短期缓存 |
| 开机任务列表 | 本地 SQLite 开机任务表 |
| 实例创建/停止/释放安全规则 | 后端 Service 调用 OCI SDK 或操作本地任务调度 |
| 首页健康状态 | `/actuator/health` 实时检查应用、数据库、JVM |
| 首页 CPU/内存/流量 | `/metrics/{token}` WebSocket 读取服务器实时指标 |
| 首页地图 | `ip_data` 表聚合，数据需通过 OCI IP 数据加载刷新 |
| 服务日志 | `/logs` WebSocket 读取应用日志文件 |
| 运维终端 | JSch 真实 SSH/SFTP 连接 |
| TGBOT 诊断/任务/日志/审计/主机 | 后端服务、日志文件、SQLite 和诊断服务真实返回 |

## 当前仍需后续专项审计

1. 旧版完整控制台仍保留为 `/legacy-dashboard.html` 降级入口，后续可继续清理无引用静态资源。
2. Google 一键登录后端能力存在，但新版登录页还未原生接入 Google Identity 流程。
3. SFTP 已补上传/下载进度反馈，仍缺大文件大小提示、断点/失败重试和更细的危险操作确认。
4. 高危 OCI 动作仍需专用测试资源逐项验收，包括终止实例、改安全规则、拆卷救援和恢复回滚。
5. 本地 Windows 环境缺少 Java 21、Maven 和 Docker，后端 Maven 编译需在 GitHub Actions 或服务器环境继续验证。

## 验证记录

```bash
npm --prefix frontend run build
```

结果：通过。`vue-tsc --noEmit` 和 Vite 生产构建均成功。

后端静态审计已完成；由于当前本地环境没有 `java`、`mvn`、`docker` 命令，本轮未在本机完成 Maven 编译。后续部署后应优先检查 GitHub Actions 构建、容器启动日志、`/actuator/health`、登录/MFA、配置详情、TGBOT 运维中心、Web SSH/SFTP、风险看板和备份恢复。
