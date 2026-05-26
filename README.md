# Wang-Detective

Wang-Detective 是面向 Oracle Cloud Infrastructure (OCI) 的 Web 管理面板和 Telegram Bot 运维助手。当前主线已经从原始 King-Detective 升级为“OCI 管理 + Web 运维 + Telegram Bot + 风险诊断 + 备份恢复 + 救援中心”的增强版控制台。

状态更新时间：2026-05-26

## 界面预览

![Wang-Detective 首页](docs/images/wang-detective-home.png)

![风险看板](docs/images/wang-detective-risk.png)

## 快速部署

```bash
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```

部署完成后访问：

- Web 面板：`http://your-server-ip:9527`
- 健康检查：`http://your-server-ip:9527/actuator/health`
- 服务器体检：

```bash
cd /app/king-detective
bash scripts/server-smoke-test.sh
```

生产环境请至少修改 `/app/king-detective/.env`：

```env
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change_me_to_strong_password
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_BOT_CHAT_ID=your_chat_id,another_chat_id
AUTO_BACKUP_ENABLED=true
AUTO_BACKUP_CRON=0 0 3 * * ?
AUTO_BACKUP_PASSWORD=your_backup_password
INSTANCE_MONITOR_ENABLED=true
INSTANCE_MONITOR_INTERVAL_MS=300000
```

修改后重启：

```bash
cd /app/king-detective
docker compose up -d --force-recreate
```

## 当前完成度

| 模块 | 完成度 | 当前状态 |
|---|---:|---|
| 部署与更新 | 91% | 安装脚本、Compose v2、低配 JVM、脚本同步、体检脚本、一键更新链路已完成 |
| Vue 新前端 | 89% | 登录、主框架、首页、配置、任务、日志、系统配置、功能中心、终端、审计、风险、备份、救援已原生化，备份空 Bucket、风险端口明细和安全规则只读入口已补齐 |
| OCI 核心管理 | 85% | 配置、任务、实例详情、实例动作、网络、安全规则、引导卷等入口已接入真实后端，线上账号已完成非破坏性接口验收 |
| Web SSH/SFTP | 88% | 主机库、主机复制/删除/批量导入、Web SSH、命令模板、危险命令确认、端口转发命令生成、会话列表、断线重连、resize、SFTP 基础文件操作和上传/下载进度反馈已完成 |
| Telegram Bot | 86% | 运维中心、诊断、任务、日志、风险、备份、版本更新、实例操作向导、多 Chat ID 和实例状态推送已接入 |
| 备份恢复 | 87% | Web 备份统一为 `backup.sh` 的 tar.gz 格式，定时自动备份和恢复计划已接入，Bucket 为空时自动退回本地备份 |
| 救援中心 | 68% | 轻量自救、boot volume 拆卷救援、netboot.xyz 实验区已上线，自动救援仍按高危能力保守推进 |
| CI/测试 | 70% | GitHub Actions 已包含 Java 21、Node 20、前端构建、Maven 构建和前后端接口映射检查 |

## 最近完成

- 新增 `scripts/remote-smoke-test.sh` 和 `scripts/remote-smoke-test.mjs` 线上验收脚本，可自动检查健康、登录、系统诊断、版本、首页概览、OCI 配置、任务、审计、备份、救援和风险接口；服务器没有 Node 时直接使用 `.sh` 版本。
- 远程验收脚本已补充 VCN 和安全规则只读接口检查，并校验风险看板返回端口暴露结构。
- 远程验收脚本已补充旧静态入口迁移检查，避免 `/ip-map.html`、`/wang-features.html`、`/ops-terminal.html` 回退到旧 UI 或静态假数据。
- JS 版远程验收脚本支持 `--transport auto|fetch|curl`，当 Node fetch 直连 Cloudflare 超时时会自动回退到 curl。
- Telegram `/terminal` 命令改为真实运维入口菜单，可直接进入 SSH 管理、主机概览、最近日志、错误日志、系统诊断和任务状态。
- 旧版静态地图 `/ip-map.html` 已停止展示模拟实例点位，统一跳转到新版 Vue 首页，避免误判为假数据。
- 旧版独立 `/wang-features.html` 和 `/ops-terminal.html` 已改为迁移提示并自动跳转 Vue 原生路由，侧边栏旧入口也统一改为“功能中心”。
- 运维终端 SFTP 上传/下载新增进度反馈和 50MB 保护阈值提示，展示文件名、已传输大小、总大小和百分比；同时清理旧前端 hash 产物，避免镜像堆积无引用资源。
- SSH 命令区新增中/高风险命令识别，高危命令需要输入 `EXECUTE` 后才会执行，降低误删、重启和清理容器风险。
- 运维终端 SSH 主机库新增新建、更新、复制、删除和批量导入，复制主机时只复制连接元数据，密码/私钥需重新补充后保存。
- 运维终端新增端口转发命令生成器，可基于选中主机复制 `ssh -N -L` 本地转发命令。
- README 新增新版首页和风险看板截图，便于部署前快速了解当前 UI。
- 备份归档页修复 Bucket 为空时的交互：没有 Object Storage Bucket 时自动禁用云端上传，只创建本地备份并给出明确提示。
- 配置列表新增“规则明细”只读入口，可查看 VCN 入站/出站安全规则，不触发放行或修改动作。
- 风险看板新增公网端口明细和收敛建议，展示 VCN、来源 CIDR、协议/端口、风险等级和建议处理方式。
- Telegram Bot 风险看板同步展示公网高危端口 Top 明细和收敛建议。
- 后端安全规则接口补充 type 参数校验，并修复缓存命中后安全规则列表可能为空的问题。
- 前端 API 请求新增统一超时机制，源站或 Cloudflare 长时间无响应时会给出明确错误，不再让登录页和按钮无限卡住。
- 登录页新增连接等待提示，引导优先检查容器健康、反向代理和 Cloudflare 源站连接。
- 修复 Telegram Bot 源码乱码，恢复中文菜单、日志查询和运维中心文案。
- 审计并修复抢机剩余数量误判、换 IP 失败日志空指针、实例监控 OCI 初始化、TG 救援列表缺少私钥内容等逻辑问题。
- 系统诊断改为优先读取环境变量和数据库配置，Telegram 已配置时不再误报未配置；SSH secret 和 AI key 不再作为必须告警项。
- 配置列表补充“实时 OCI 操作”提示条，展示选中配置、当前操作处理中状态，并统一禁用高危/并发操作入口。
- 全局按钮强化点击反馈、禁用态、窄屏抗卡字样式；系统配置和功能中心补齐加载中、发送中、刷新中等交互状态。
- README 已整理为当前状态版，旧流水账迁移到文档索引中继续保留。

## 已完成能力

- Docker 镜像统一为 `ghcr.io/tony-wang1990/wang-detective:main`。
- 持久化目录统一为 `data/`、`keys/`、`logs/`、`runtime/`、`backups/`。
- 修复部署挂载覆盖 JAR、数据库迁移、SQLite 分页、WebSocket 日志、VCN/引导卷分页 total、任务前缀隔离等基础问题。
- 新增增强健康检查 `/actuator/health` 和系统诊断 `/api/v1/system/diagnostics`。
- 新增可维护 Vue 前端源码 `frontend/`，生产入口已切到新版 Vue。
- 配置列表接入真实 OCI 操作：启动、停止、重启、改名、换 IP、IPv6、VNC、500M、Shape、CPU/内存、引导卷、终止实例等。
- 新增 Web SSH、SSH 主机库、单命令、批量命令、命令模板、会话列表、断线重连、终端 resize。
- 新增 SFTP 浏览、读取、写入、上传、下载、重命名和删除确认。
- 新增操作审计页，支持搜索、筛选、详情和 CSV 导出。
- 新增 OCI 风险看板 `/api/v1/oci/risk`。
- 风险看板已包含公网端口暴露明细、端口风险等级和收敛建议。
- 新增备份归档页 `/api/v1/backups/*`，支持本地备份、Object Storage 归档、恢复计划、定时备份方案。
- 配置列表新增安全规则只读明细入口，支持按 VCN 查看入站/出站安全列表。
- 新增救援中心 `/dashboard/rescue`，API 路径统一为 `/api/rescue/*`，提供轻量自救、boot volume 拆卷救援、netboot.xyz 实验区。
- Telegram Bot 运维中心支持系统诊断、任务状态、最近日志、错误日志、审计摘要、主机概览、风险看板、备份归档、版本更新和实例操作向导。
- Telegram Bot 风险看板已同步 Web 风险接口，可展示高危公网端口、来源 CIDR、VCN 和收敛建议。
- CI 发布前执行脚本语法检查、前端 API 到后端 Controller 映射检查、前端 build、Maven package、Docker build。

## 未完成和后续重点

| 优先级 | 事项 | 说明 |
|---|---|---|
| P0 | 真实 OCI 高危操作验收 | 非破坏性接口已通过线上验收；终止实例、改安全规则、拆卷救援、恢复回滚等高危动作仍需在专用测试资源上逐项验收 |
| P0 | 前端交互验收 | 继续检查全部页面的按钮卡字、点击反馈、loading、错误提示、暗色模式和移动端布局，尤其是移动端 |
| P1 | 低配 VPS 启动体验 | 低配机器首次启动约 60-90 秒属正常范围，但登录等待提示、启动页反馈仍可继续优化 |
| P1 | 备份恢复闭环 | Web 恢复/回滚按钮需要更严格的二次确认、权限边界和真实恢复验收 |
| P1 | TGBOT 可控执行 | Bot 已能查看和部分执行实例动作，后续继续补完整权限模型、冷却时间和管理员确认 |
| P1 | 救援中心一键操作 | 轻量自救和向导已上线，高危的停机、拆卷、挂载、回滚仍需真实机型测试后逐步开放 |
| P2 | netboot.xyz 自动救砖 | 当前只做安全向导和实验区，不自动改 bootloader；需实测 AMD/ARM、UEFI/BIOS 后再开放一键引导 |
| P2 | API 契约测试 | 继续补 Controller Mock、Bot 回调和脚本端到端测试，减少上线后才发现接口问题 |

## 常用命令

```bash
cd /app/king-detective

# 查看服务和日志
docker compose ps
docker logs -f king-detective

# 手动更新、回滚、体检
bash scripts/update.sh
bash scripts/rollback.sh ghcr.io/tony-wang1990/wang-detective:main
bash scripts/server-smoke-test.sh

# 备份、恢复、支持包
bash scripts/backup.sh
bash scripts/restore.sh /app/king-detective/backups/wang-detective-backup-YYYYmmdd-HHMMSS.tar.gz
bash scripts/support-bundle.sh

# 远程线上验收（不要把真实密码写入仓库）
WANG_DETECTIVE_BASE_URL=https://your-domain.example \
WANG_DETECTIVE_USERNAME=admin \
WANG_DETECTIVE_PASSWORD='your-password' \
bash scripts/remote-smoke-test.sh

# 本机有 Node 20+ 时也可使用 JS 版
node scripts/remote-smoke-test.mjs
```

## 文档索引

- 部署验收：[docs/DEPLOYMENT_SMOKE_TEST.md](docs/DEPLOYMENT_SMOKE_TEST.md)
- 代码审计：[docs/CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md)
- 项目路线：[docs/PROJECT_PROGRESS_ROADMAP.md](docs/PROJECT_PROGRESS_ROADMAP.md)
- UI 路线：[docs/UI_REDESIGN_ROADMAP.md](docs/UI_REDESIGN_ROADMAP.md)
- 救援/netboot 路线：[docs/RESCUE_NETBOOT_ROADMAP.md](docs/RESCUE_NETBOOT_ROADMAP.md)
