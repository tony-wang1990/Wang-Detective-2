# Wang-Detective

Wang-Detective 是基于 King-Detective 增强的 Oracle Cloud Infrastructure (OCI) 管理面板和 Telegram Bot。它保留原有的 OCI 配置管理、开机任务、实例管理、IP/IPv6、VNC/救援、引导卷、安全规则、Cloudflare DNS、流量统计、备份恢复、MFA 和基础 AI 助手，并补上部署稳定性、诊断能力和运维入口。

## 快速部署

```bash
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```

部署完成后访问：

- Web 面板：`http://your-server-ip:9527`
- 功能中心：`http://your-server-ip:9527/dashboard/features`
- 运维终端：`http://your-server-ip:9527/dashboard/ops-terminal`
- 健康检查：`http://your-server-ip:9527/actuator/health`
- 系统诊断：`GET /api/v1/system/diagnostics`，需要登录 token

版本显示说明：新版 Docker 镜像会把构建提交号写入运行版本，例如 `main-b2a3717`；页面上的“最新版本”会查询 `Wang-Detective/main` 最新提交。这样每次修复 BUG 并重建镜像后，都能看到可追踪的版本号，不再显示旧项目 release 的 `null`。

默认账号密码兼容旧版本：`admin / admin123456`。生产环境请立即在 `/app/king-detective/.env` 中修改：

```env
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change_me_to_strong_password
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_BOT_CHAT_ID=your_chat_id
```

然后重启：

```bash
cd /app/king-detective
docker compose up -d --force-recreate
```

## 当前阶段状态

本阶段已经完成第一轮稳定性修复、部署修复、运维入口一期、主面板新功能入口、UI 重设计第一阶段和发布前代码审计第一轮。当前仓库 `main` 是可继续部署验证的阶段版本，后续开发会继续以 UI 深化、运维审计和 OCI 能力补齐为主线推进。

已完成重点：

- Docker 部署链路已切到 `ghcr.io/tony-wang1990/wang-detective:main`，修复旧 compose、旧容器残留、低配 VPS 启动慢、IPv6 监听和版本 `null` 等问题。
- 后端增强已落地：健康检查、系统诊断、数据库迁移修复、SQLite 分页、WebSocket 日志、VCN/引导卷分页 total、任务前缀隔离。
- 运维入口一期已落地：Web SSH、SSH 主机库、批量命令、SFTP 基础文件操作、操作审计基础表和接口。
- UI 可见性已修复：左侧菜单增加“功能中心”和“运维终端”，并进入主面板 Vue 路由，不再默认跳到独立页面。
- UI 重设计第一阶段已落地：新增现代控制台主题、重做登录页视觉、改造侧边栏/顶部栏/首页卡片，并为首页增加顶部搜索/健康/版本区、地图+系统诊断双栏、资源使用面板。
- Vue 原生化已开始接管生产入口：`frontend/` 为可维护源码，登录页、主框架、首页、配置/任务/日志/系统配置/AI/功能中心/运维终端已进入 Vue 路由。
- 2026-05-17 完成代码审计第一轮：核对前后端 API 映射、Vue 路由、TGBOT 回调覆盖、OCI 实时详情入口和新增运维能力，修复 MFA 登录缺口、配置新增/实时详情、TGBOT 回调前缀冲突、分页/创建实例返回按钮无处理器、TGBOT 运维中心审计/主机概览入口。
- 2026-05-17 完成前端操作审计页原生化：新增 `/dashboard/ops-audit`，接入 `/api/ops/audit/recent`，支持审计摘要、搜索、状态筛选、条数切换和详情查看。
- 2026-05-18 修复 Web/TGBOT 一键更新链路：安装脚本默认启动 `king-detective-watcher`，后端检测 watcher 心跳，更新失败会给明确提示；同时修复诊断页 Telegram 误报、按钮卡字和登录阶段同步查 GitHub 导致的卡顿。
- 2026-05-18 继续完成前端交互打磨：资源页真实 OCI 高危操作、任务停止/批量停止和顶部版本更新均改为页面内确认弹窗/状态提示，移除浏览器原生 `prompt/confirm/alert`；TGBOT 启动和诊断新增 `TELEGRAM_CHAT_ID` 环境变量兜底，减少“已配置但误报未配置”的情况。

当前仍未完成、后续必须重点推进：

- **UI 重建和完善仍是下一阶段最大重点。** 生产入口已切到新 Vue，首页、配置、任务、日志、系统配置、运维终端均可用，但仍需要继续做视觉细节、移动端适配、更多空状态/错误状态和旧版高级操作补齐。
- 配置列表已接入新增 OCI 配置、真实分页、测活、实时详情、改名、删除、放行和停任务；后续还要补完整实例操作入口、批量策略和更友好的 OCI 错误解释。
- 首页已接入 Leaflet 地图、健康检查和 `/metrics/{token}` 实时指标；后续要继续做 Always Free/配额风险卡片和跨区域资源摘要。
- 运维终端已具备 Vue 内 Web SSH、单命令、命令模板、命令历史、会话列表、断线重连、终端 resize、SFTP 上传/下载/重命名/删除和删除二次确认；操作审计已进入左侧菜单；后续要补大文件进度、端口转发和权限分级。
- 旧版完整控制台和旧 bundle 暂时保留为回退入口，验证稳定后再清理。
- 移动端/窄屏布局还需要专项优化。
- Telegram Bot 已新增运维中心、系统诊断、任务状态、最近日志、审计摘要、主机概览和快捷入口；后续建议继续增加安全确认后的快捷命令、SSH 主机分组和异常告警订阅。

## 2026-05-18 大版本：真实 OCI 操作闭环

本版本把后续重点一次性转向“能真实操作、能看见反馈、能从 Web 和 TGBOT 都进入运维动作”的大闭环，不再只停留在展示和入口层。

本次完成：

- 配置列表页升级为 OCI 实时操作台：从 `/api/oci/details` 读取真实 OCI 实例、Cloudflare 配置和 NLB 数据。
- 前端补齐真实 OCI 操作入口：批量开机、实例启动/停止/重启、改名、换 IP、停止换 IP、创建 IPv6、启动 VNC、自动救援、开启/关闭 500M、读取实例配置、调整 CPU/内存、修改 Shape、调整引导卷、发送终止验证码并终止实例。
- 登录页和主框架修正乱码与品牌呈现：登录页左侧升级为清晰 W-探长品牌区，主框架菜单、状态、版本、主题、退出等中文恢复正常。
- 顶部搜索不再是静态假输入框：输入页面关键词后回车可跳转到对应 Vue 页面。
- TGBOT 运维中心升级：新增实例概览、版本更新状态、错误日志入口；保留系统诊断、任务状态、最近日志、操作审计、SSH 主机概览、快捷运维等入口。
- TGBOT 实例概览会调用已有 OCI 实例查询服务，默认扫描前 5 个配置，兼顾真实性和 Bot 回调响应时间。
- TGBOT 版本更新入口显示当前版本、最新版本、watcher 状态和更新按钮，和 Web 自动更新链路保持一致。
- 修复运维中心与版本信息 Bot 文案乱码，避免菜单显示异常。
- 前端生产包已重新构建并同步到 `src/main/resources/dist`。

本次验证：

```bash
npm --prefix frontend run build
```

本地 Windows 环境当前没有 `java`、`mvn` 和 `docker` 命令，所以后端编译仍依赖 GitHub Actions 或服务器环境继续验证。后续如果要在本机完整验证，需要先安装 Java 21 + Maven 或使用可用 Docker 环境。

后续仍建议继续完善：

- 继续把更多高频按钮补齐提交中、成功、失败和空状态反馈，尤其是 OCI 批量操作、筛选查询和运维终端的大文件操作。
- 给高危 OCI 操作增加更细的二次确认和操作审计字段，例如实例 OCID、配置名、操作者、参数快照。
- 实例列表增加批量选择和批量操作策略，例如批量启动/停止/改名模板/批量换 IP。
- TGBOT 下一步可继续做“选择配置 -> 选择实例 -> 执行动作”的完整实例操作向导，目前本版本优先完成运维中心概览和跳转入口。
- 本机或 CI 继续补充 Java 编译与后端单元测试，重点覆盖 TGBOT 新增 handler、版本更新链路和 OCI 参数映射。

## 数据目录

增强版统一使用可持久化目录，避免更新镜像时丢数据：

- 数据库：`/app/king-detective/data/king-detective.db`
- OCI 私钥：`/app/king-detective/keys/`
- 应用日志：`/app/king-detective/logs/king-detective.log`
- 更新触发器：`/app/king-detective/runtime/update_version_trigger.flag`
- 环境变量：`/app/king-detective/.env`

## 本版增强

- 修复 Docker Compose 挂载整个工作目录导致镜像内 JAR 被隐藏的问题。
- 统一 `data/`、`keys/`、`logs/`、`runtime/` 持久化目录。
- 修复 watcher 更新触发、数据库版本写入和 `oci_kv.id` 缺失问题；新版安装会默认启动 watcher，Web/TGBOT 一键更新依赖它执行镜像拉取和容器重建。
- 修复数据库迁移 SQL 被注释跳过和无效索引问题。
- 启用 MyBatis Plus SQLite 分页插件。
- 修复 VCN、引导卷分页 total 只返回当前页数量的问题。
- 隔离开机任务和换 IP 任务的内存键前缀，避免互相覆盖。
- 强化日志 WebSocket token 校验、非法连接关闭、历史日志推送和日志文件初始化。
- 增强 `/actuator/health`，返回版本、运行时长、数据库和 JVM 内存状态。
- 新增 `/api/v1/system/diagnostics`，检查数据库、数据目录、密钥目录、日志、默认密码、Telegram 配置、磁盘和内存；AI Key 和 SSH 加密密钥属于可选能力，不再作为告警噪音显示。
- 新增一期运维入口：Web SSH 终端、SSH 单命令、批量命令、SFTP 列表/读取/写入/上传/下载/重命名/删除。
- 新增 SSH 主机资产库：保存常用主机、AES-GCM 加密保存密码/私钥、通过 `hostId` 复用凭据。
- 新增发布前代码审计修复：MFA 登录、配置新增、配置 OCI 实时详情、TGBOT 回调覆盖、操作审计和主机概览。
- 新增 Vue 原生操作审计页：左侧菜单“操作审计”，支持最近审计流水、成功/失败统计、来源 IP 统计、关键字搜索、状态筛选和详情查看。
- 增强运维终端：新增常用 SSH 命令模板、本地命令历史、Web SSH 会话列表、断线重连、终端 resize 同步和 SFTP 删除前 `DELETE` 二次确认。

## 运维终端

登录 Web 面板后，可以直接打开：

```text
http://your-server-ip:9527/dashboard/ops-terminal
```

页面会自动读取浏览器 `sessionStorage` 中的登录 token，也可以手动粘贴 token。当前一期能力包括：

- 测试 SSH 连接。
- 保存/更新常用 SSH 主机，后续直接选择主机执行操作。
- 打开交互式 Web SSH 终端，支持会话列表、断线后重连和终端尺寸同步。
- 执行单台机器命令并返回 stdout/stderr/exit status。
- 批量对多台机器执行同一条命令。
- SFTP 浏览目录、读取小文本文件、写入文件、上传/下载文件、创建目录、重命名和删除。

安全提醒：保存的 SSH 密码/私钥会使用 AES-GCM 加密后写入数据库。建议生产环境显式配置稳定的 `OPS_SSH_SECRET_KEY`，否则默认会从 Web 管理密码派生密钥；如果后续修改管理密码，旧的保存主机可能无法解密。Web SSH 会话凭据仍仅短时保存在服务端内存中。后续会继续补充操作审计、权限控制、端口转发和大文件传输进度。

## 常用命令

```bash
cd /app/king-detective

# 查看状态
docker compose ps

# 查看日志
docker logs -f king-detective

# 重启
docker compose restart king-detective

# 手动更新
TELEGRAM_BOT_TOKEN="xxx" ADMIN_USERNAME="admin" ADMIN_PASSWORD="strong_password" bash update.sh
```

## 部署问题处理

如果安装时卡在 `Pulling websockify ... error`，说明服务器上保留了早期增强版的旧 `docker-compose.yml`，其中引用了未发布的 `king-detective-websockify` 镜像。新版默认部署已移除该非必需服务，并会自动备份旧 compose 后刷新。

如果 1C/1G 左右的 VPS 部署后长时间 `health: starting` 或 `unhealthy`，通常是首次 Spring 初始化太慢，不一定是程序崩溃。新版默认限制 JVM 使用 1 个 CPU、384MB 堆内存、IPv4 监听，并把健康检查启动宽限延长到 10 分钟；首次启动 1 分钟左右在低配 VPS 上属于正常范围。

如果启动时报 `KeyError: 'ContainerConfig'`，这是旧版 `docker-compose 1.29.x` 重建新版 GHCR/BuildKit 镜像时的兼容问题。新版安装脚本会优先使用 Docker Compose v2，并在启动前移除旧容器后重新创建；数据目录通过 bind mount 持久化，不会因为删除容器而丢失。

可在服务器上执行：

```bash
cd /app/king-detective
cp docker-compose.yml docker-compose.yml.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true
rm -f docker-compose.yml
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```

查看自动更新 watcher 日志：

```bash
cd /app/king-detective
docker logs -f king-detective-watcher
```

## 本地验证

本项目按 Java 21 构建：

```bash
mvn -DskipTests compile
mvn test
mvn package
```

## 后续路线

对比 R-Bot / java_oci_manage 等同类 OCI 运维工具后，下一阶段建议按这个顺序继续：

1. UI 重建和完善：恢复/重建 Vue 前端源码，把“功能中心”“运维终端”做成真正 Vue 路由；全量重绘登录页、首页、配置列表、任务列表、日志、系统配置、AI 聊天室和运维页面。
2. 运维入口二期：操作审计筛选/导出、权限控制、命令模板、端口转发、大文件传输进度和断点续传。
3. Telegram Bot 增强：系统诊断摘要、最近审计、任务状态、主机列表、快捷命令菜单和危险操作二次确认。
4. OCI Object Storage：Bucket/Object 管理、数据库备份归档、日志归档、临时下载链接。
5. OCI Email Delivery：DKIM/SPF 指引、SMTP 凭据检查、测试发信。
6. 成本、配额、Always Free 用量和超额风险看板。
7. 多云只读资产发现，优先支持 AWS/GCP/Azure/DO 的实例同步。

详细改造记录见 [docs/ENHANCEMENT_REPORT.md](docs/ENHANCEMENT_REPORT.md)。

当前进度、UI 集成约定和后续计划见 [docs/PROJECT_STATUS_AND_UI_GUIDE.md](docs/PROJECT_STATUS_AND_UI_GUIDE.md)。

UI 重设计路线和后续拆分见 [docs/UI_REDESIGN_ROADMAP.md](docs/UI_REDESIGN_ROADMAP.md)。

代码审计、API 映射和本轮修复记录见 [docs/CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md)。

## 2026-05-18 更新链路与 UI 细节修复

本次针对部署后的真实反馈做快速修复：

- 自动更新 watcher 从可选 profile 改为默认部署服务，安装脚本会同时拉取并启动 `king-detective` 和 `king-detective-watcher`。
- watcher 新增 `runtime/watcher_heartbeat` 心跳文件；Web/TGBOT 点击更新前会检测 watcher 是否在线，避免“已触发但无人执行”的假成功。
- watcher 镜像拉取、版本查询和数据库版本写入全部切回 `tony-wang1990/Wang-Detective`，不再引用旧的 `king-detective` 仓库。
- 修复安装脚本清理旧容器时误删 `king-detective-watcher` 的问题。
- 系统诊断改为读取网页保存的 Telegram Token/Chat ID，避免已经配置 TGBOT 却仍显示未配置；OpenAI Key 和 SSH Secret Key 不再作为告警项。
- 登录接口不再同步访问 GitHub 查询最新版本，减少登录按钮长时间卡住的情况；最新版本仍由控制台顶部异步检测。
- 统一按钮最小宽度、禁止中文按钮换行，补充 hover/active/focus 反馈，修复“查询”“上一页/下一页”等按钮卡上下字的问题。
- 顶部菜单按钮已变为真实折叠侧边栏操作，不再是无反馈按钮。

## 2026-05-17 操作审计页原生化

本次在审计修复通过构建和部署后，继续完成下一块运维可视化能力：

- 新增 `/dashboard/ops-audit` Vue 原生路由，并在左侧菜单增加“操作审计”入口。
- 接入后端 `/api/ops/audit/recent`，展示最近 50/100/200/500 条审计记录。
- 页面支持关键字搜索、成功/失败状态筛选、最近刷新时间和空状态提示。
- 顶部摘要展示总记录数、成功/失败数、来源 IP 数和最近操作时间。
- 右侧详情面板展示操作类型、目标、操作者、来源 IP、User Agent、详情 JSON 或失败原因。
- 明暗主题沿用现有控制台变量，和首页、日志、运维终端保持一致。
- 已通过 `npm --prefix frontend run build`，并同步更新生产 dist 资源。

## 2026-05-17 运维终端体验增强

本次继续深化 `/dashboard/ops-terminal`：

- SSH 命令区新增常用模板：系统概览、Docker 状态、应用日志和端口监听。
- 执行过的 SSH 命令会保存在浏览器本地历史中，最多保留最近 6 条，便于重复执行。
- Web SSH 新增会话列表，后端短时保存会话凭据，前端可查看当前可重连会话。
- Web SSH 新增断线重连，断开后可从当前会话或会话列表重新建立终端连接。
- Web SSH 新增终端 resize，同步列/行尺寸到 JSch shell，支持手动填写和根据终端区域自适应。
- SFTP 删除操作取消浏览器弹窗，改为页面内输入 `DELETE` 的二次确认。
- 删除确认区会展示当前选中的路径，减少误删远程文件或目录的风险。
- 已通过 `npm --prefix frontend run build`，并同步更新生产 dist 资源。

## 2026-05-17 Web/TGBOT 自动更新增强

本次把版本检测和一键更新统一到 Web 与 Telegram Bot：

- Web 顶栏会自动调用 `/api/v1/system/version-info` 检测 `Wang-Detective/main` 最新构建版本。
- 检测到新版本时，Web 顶栏显示“更新 latestVersion”按钮，点击后调用 `/api/v1/system/trigger-update` 写入 watcher 触发文件。
- TGBOT “版本信息”菜单会显示当前版本、最新版本、更新状态和“点击更新到最新版本”按钮。
- TGBOT 每日自动版本检测发现新版本时，会发送带 Inline Button 的通知，可直接点击触发更新。
- Web、系统服务和 TGBOT 均统一写入 `/app/king-detective/runtime/update_version_trigger.flag`，避免旧路径导致 watcher 不响应。
- 已通过 `npm --prefix frontend run build`；本地仍缺少 Java/Maven，后端编译由 GitHub Actions 构建继续验证。

## 2026-05-17 代码审计与功能修复

本轮优先做全项目第一轮发布前审计，覆盖 Controller、Vue API 调用、Vue Router、WebSocket、OCI SDK 调用链和 Telegram Bot 回调菜单。

- 前端当前使用的 `/api/*` 调用已核对后端 Controller，未发现缺失映射。
- Telegram Bot 按钮回调已静态扫描，当前按钮入口均有处理器或明确的 no-op 处理。
- 修复 TGBOT 回调工厂的前缀匹配风险，避免短 pattern 抢先处理长 pattern。
- 修复分页按钮 `page_info` 没有处理器的问题。
- TGBOT 运维中心新增“操作审计”和“主机概览”，并加入快捷运维入口。
- 新版登录页补齐 MFA 输入流程，启用 MFA 后会先读取状态并显示验证码输入框。
- 配置列表补齐新增 OCI 配置表单，提交时调用 `/api/oci/addCfg` 上传 config 内容和私钥文件。
- 配置详情从本地 JSON 预览升级为调用 `/api/oci/details`，并强制刷新 OCI 实时实例/NLB 数据。
- 新增 `docs/CODE_AUDIT_REPORT.md`，记录本轮审计结论、真实性说明、修复项和后续风险。
- 已通过 `npm --prefix frontend run build`；本地环境缺少 Java/Maven/Docker，后端编译需继续由 GitHub Actions 或服务器环境验证。

## 2026-05-14 任务列表原生化增强

本次继续推进 Vue 原生控制台，完成“任务列表”的第二轮增强：

- 任务列表接入真实 `/oci/createTaskPage` 分页参数，支持关键字搜索、架构筛选、页码和每页数量切换。
- 新增任务多选、批量停止、单任务停止和任务详情 JSON 预览。
- 新增 ARM/AMD 架构标识、尝试次数、运行中状态和规格聚合展示，页面风格跟随当前明暗主题。
- 修复后端开机任务分页参数兜底：页码小于 1 或 pageSize 为空时不再产生异常偏移，并限制单页最大 100 条。
- 修复任务 SQL 搜索条件优先级，避免架构筛选与关键字 OR 条件互相串扰。

下一步继续按同一节奏做“服务日志”页面：补齐日志筛选、刷新控制、WebSocket 状态提示、历史日志加载和暗色模式细节。

## 2026-05-15 运维页面与 TGBOT 菜单增强

本次把 UI 原生化继续推进到服务日志页、系统配置页，并同步增强 Telegram Bot 运维入口：

- 服务日志页升级为新版 Vue 原生日志控制台：连接状态、暂停显示、重载历史、关键字搜索、日志级别筛选、行数上限、自动滚动、WARN/ERROR 统计和明暗主题一致适配。
- 系统配置页从“字段循环”深化为分区配置：通知通道、自动化与版本、安全与登录、测试工具、系统诊断和原始接口返回；布尔项改为开关式编辑，MFA 二维码和诊断摘要可直接查看。
- 系统诊断逻辑抽成 `SystemDiagnosticsService`，Web 接口和 Telegram Bot 共用同一份诊断结果，减少后续维护分叉。
- Telegram Bot 主菜单新增“运维中心”，并增加系统诊断、任务状态、最近日志、日志文件发送和快捷运维入口。
- Telegram 最近日志支持直接在 Bot 消息中查看最近 30 行；完整日志仍保留原来的文件发送方式。
- Telegram 任务状态会汇总当前开机任务数量、架构分布、前 8 个任务的配置、区域、规格和尝试次数，并可跳转原任务管理页继续批量停止。

AI 聊天室本轮保持现状，不做体验重构和功能升级。

## 2026-05-13 UI 原生化阶段更新

本次继续推进 UI 重建主线，但保持线上可部署版本稳定：

- 修复 `scripts/install.sh` 被 Windows CRLF 换行污染的问题，避免 Linux 上执行 raw 脚本时报 `$'\r': command not found` 和 `syntax error near unexpected token elif`。
- 扩展 `.gitattributes`，强制 `.sh`、前端源码、HTML/CSS/JS/JSON 等文本文件使用 LF，降低后续再次混入 CRLF 的概率。
- 修复新增模块暗色模式：`功能中心`、`运维终端`、首页新增诊断卡片和内嵌 iframe 会跟随主系统开关灯变化，不再出现暗色外壳里一片白色的页面。
- 新增 `frontend/` 可维护 Vue 源码目录，已落地登录页、控制台主框架、首页、基础路由、主题切换和 API 封装。
- 新 Vue 源码当前构建到 `src/main/resources/dist-next`，暂不替换生产入口 `src/main/resources/dist`。后续等页面迁移完整后，再把 Maven/Docker 构建切到新前端产物。

验证记录：

```bash
npm --prefix frontend install
npm --prefix frontend run build
```

后续第一优先级仍然是 UI 重建和 Vue 原生化：把 `功能中心`、`运维终端` 从静态 HTML/iframe 迁成真正 Vue 路由，再逐步重做配置列表、任务列表、服务日志、系统配置和 AI 聊天室页面。

## 2026-05-14 UI 原生化收口

本次把新 Vue 前端切为正式生产入口，完成今天这一整块 UI 原生化迁移：

- `frontend/` 已成为可维护前端源码，`npm --prefix frontend run build` 直接输出到 `src/main/resources/dist`。
- 登录页、主控制台框架、首页、配置列表、任务列表、服务日志、系统配置、AI 聊天室、功能中心、运维终端都已接入 Vue 原生路由。
- `/dashboard/features` 和 `/dashboard/ops-terminal` 不再依赖 iframe 作为主入口。
- 顶部系统健康状态已改为读取 `/actuator/health`，版本号同步使用健康检查返回值，避免再显示硬编码状态。
- 首页已接入真实 Leaflet 地图和 ECharts 资源图表：使用 `/api/sys/glance` 的城市数据渲染地图点位，并通过 `/metrics/{token}` 实时刷新 CPU、内存和网络流量。
- 配置列表已完成第二轮 Vue 原生化：补齐搜索、开机任务筛选、分页、测活、批量删除、改名、停止开机任务、安全列表放行和详情预览。
- 运维终端已补上 Vue 内交互式 Web SSH：创建会话后直接连接 WebSocket，支持命令输入、发送、Ctrl+C 和断开。
- 运维终端 SFTP 已补齐 Vue 内文件操作：目录浏览、文本读取/编辑保存、上传、下载、新建目录、删除和重命名。
- 登录页左侧品牌区已增强为更明显的 W-探长标识和 OCI 运维控制台信息，不再只是左上角小字。
- 前端接口错误处理已增强，后端返回 `msg/message` 或 `success:false` 时会在页面显示更明确的错误。
- 旧版完整控制台临时保留为 `/legacy-dashboard.html`，便于明天部署测试时对照和回退查看旧功能细节。
- 旧的打包资源暂时保留，等新页面路由细节全部验证通过后再删除旧 bundle 和过渡脚本。

本阶段只做最小验证，已通过：

```bash
npm --prefix frontend run build
```

明天优先测试：登录跳转、暗色/亮色切换、配置列表分页、任务列表、日志 WebSocket、系统配置保存、AI 流式响应、运维终端 SSH/SFTP 真实连接。
