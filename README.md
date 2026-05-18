# Wang-Detective

Wang-Detective 是面向 Oracle Cloud Infrastructure (OCI) 的 Web 管理面板和 Telegram Bot 运维助手。当前版本基于 King-Detective 做了部署稳定性、Vue 新前端、真实 OCI 操作入口、Web SSH/SFTP、操作审计、风险看板、备份归档和自动更新能力增强。

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

默认账号兼容旧版本：`admin / admin123456`。生产环境请立刻修改 `/app/king-detective/.env`：

```env
ADMIN_USERNAME=admin
ADMIN_PASSWORD=change_me_to_strong_password
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_BOT_CHAT_ID=your_chat_id
```

修改后重启：

```bash
cd /app/king-detective
docker compose up -d --force-recreate
```

## 当前状态

状态更新：2026-05-18

当前项目已经从原始 OCI 管理面板升级为“OCI 管理 + Web 运维 + Telegram Bot + 风险/备份/审计”的增强版控制台。主线功能已经能部署、能登录、能进入 Vue 新界面，也具备真实 OCI 操作入口和基础运维闭环。

整体成熟度估算：

| 模块 | 完成度 | 状态 |
|---|---:|---|
| 部署与更新 | 85% | 安装脚本、compose、watcher、低配 VPS 参数、脚本同步和目录冲突已修复 |
| Web 新前端 | 70% | Vue 主框架已接管生产入口，仍需继续打磨细节和移动端 |
| OCI 核心管理 | 75% | 配置、任务、实例实时详情和高频实例操作已接入，仍需继续做全量验收 |
| Web SSH/SFTP | 75% | 主机库、Web SSH、命令模板、会话重连、SFTP 基础操作已完成 |
| 操作审计 | 75% | 运维操作、命令模板和本轮新增 OCI 高危操作审计已接入 |
| 风险看板 | 60% | 已有 OCI 风险扫描入口，后续还要补配额/成本/Always Free 深化 |
| 备份归档 | 65% | 本地备份和 OCI Object Storage 归档入口已完成，恢复流程还需产品化 |
| Telegram Bot | 70% | 运维中心、诊断、任务、日志、风险、备份、更新入口已完成，实例操作向导待补 |
| 测试与发布 | 55% | 已有发布验证脚本和服务器体检脚本，后续要补 CI 后端编译和接口测试 |

## 已完成

- Docker 部署链路切换到 `ghcr.io/tony-wang1990/wang-detective:main`。
- 统一持久化目录：`data/`、`keys/`、`logs/`、`runtime/`。
- 修复数据库迁移、SQLite 分页、WebSocket 日志、VCN/引导卷分页 total、任务前缀隔离等基础问题。
- 新增 `/actuator/health` 增强健康检查和 `/api/v1/system/diagnostics` 系统诊断。
- 新增 Vue 前端源码目录 `frontend/`，生产入口已切到新版 Vue。
- 已原生化页面：登录页、主框架、首页、配置列表、任务列表、服务日志、系统配置、功能中心、运维终端、操作审计、风险看板、备份归档。
- 配置列表已接入真实 OCI 实时详情和实例操作入口：启动、停止、重启、改名、换 IP、IPv6、VNC、500M、Shape、CPU/内存、引导卷、终止实例等。
- 高危 OCI 操作已改为页面内确认弹窗，逐步移除浏览器原生 `alert/confirm/prompt`。
- 新增全局请求状态和 toast，前端按钮点击后会有更明确的请求中、成功、失败反馈。
- 新增 Web SSH、SSH 主机库、单命令、批量命令、命令模板、会话列表、断线重连和终端 resize。
- 新增 SFTP 浏览、读取、写入、上传、下载、重命名和删除确认。
- 新增操作审计页，支持搜索、状态筛选、详情查看和 CSV 导出。
- 新增 OCI 风险看板和 `/api/v1/oci/risk`。
- 新增备份归档页和 `/api/v1/backups/*`，支持本地备份包和 Object Storage 归档。
- 新增服务器脚本工具箱：备份、恢复、更新、回滚、支持包、维护菜单、发布验证和服务器冒烟检查。
- 修复安装脚本同步脚本时遇到 `scripts/watcher.sh` 是目录导致部署中断的问题。
- Telegram Bot 已增加运维中心、系统诊断、任务状态、最近日志、审计摘要、主机概览、风险看板、备份归档和版本更新入口。

## 仍未完成

这些是当前剩余重点，不包含后续“救援中心 / netboot.xyz”专项：

1. **前端细节继续打磨**
   - 全站按钮高度、图标、loading、禁用态、空状态、错误态还要逐页过一遍。
   - 暗色/亮色模式还需要继续检查所有新页面。
   - 移动端和窄屏布局还没有专项完成。

2. **OCI 操作全量验收**
   - 需要继续逐个验证所有前端按钮是否真实调用后端、后端是否真实调用 OCI SDK。
   - 需要补更友好的 OCI 错误解释，例如容量不足、配额不足、认证失败、区域不可用。
   - 批量实例操作、批量策略和失败重试建议还不够完整。

3. **Telegram Bot 实例操作向导**
   - 当前 Bot 已有运维入口和摘要能力。
   - 还缺完整的“选择配置 -> 选择实例 -> 确认动作 -> 执行 OCI 操作”的闭环。
   - 高危动作需要更严格的二次确认和权限限制。

4. **备份恢复产品化**
   - 已能创建备份和归档对象存储。
   - Web 上的恢复、回滚、备份策略、定时任务管理还需要继续做成完整流程。

5. **发布质量和自动化测试**
   - 服务器体检脚本已有，但 CI 还需要更严格。
   - 后续要补 Maven 后端编译、前端 build、Docker compose 配置校验、关键 API 契约测试、TGBOT 回调测试。

6. **旧资源清理**
   - 旧版 dashboard 静态包仍保留为回退入口。
   - 等新版 Vue 稳定后，需要逐步删除旧 bundle 和历史兼容入口，降低维护混乱。

## 下一步计划

建议按下面顺序推进：

1. 全站 UI/交互验收：按钮卡字、点击反馈、loading、错误提示、暗色模式、移动端。
2. OCI 真实操作验收：逐个核实配置、任务、实例、网络、安全规则、引导卷、备份相关按钮。
3. TGBOT 实例操作向导：把 Bot 从“查看和入口”推进到“可控执行”。
4. 备份恢复闭环：Web 恢复、回滚、定时备份和 Object Storage 归档策略。
5. CI/测试增强：让 GitHub Actions 先发现构建、接口和脚本问题。
6. 救援中心专项：轻量自救、boot volume 拆卷救援、netboot.xyz 实验区。

## 常用命令

```bash
cd /app/king-detective

# 查看服务
docker compose ps

# 查看日志
docker logs -f king-detective

# 重启服务
docker compose restart king-detective

# 手动更新
bash scripts/update.sh

# 服务器体检
bash scripts/server-smoke-test.sh

# 维护菜单
bash scripts/maintenance.sh menu

# 备份 / 恢复 / 支持包
bash scripts/backup.sh
bash scripts/restore.sh /app/king-detective/backups/wang-detective-backup-YYYYmmdd-HHMMSS.tar.gz
bash scripts/support-bundle.sh
```

## 重要文档

- 部署验收：[docs/DEPLOYMENT_SMOKE_TEST.md](docs/DEPLOYMENT_SMOKE_TEST.md)
- 代码审计：[docs/CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md)
- 项目路线：[docs/PROJECT_PROGRESS_ROADMAP.md](docs/PROJECT_PROGRESS_ROADMAP.md)
- UI 路线：[docs/UI_REDESIGN_ROADMAP.md](docs/UI_REDESIGN_ROADMAP.md)
- 救援/netboot 路线：[docs/RESCUE_NETBOOT_ROADMAP.md](docs/RESCUE_NETBOOT_ROADMAP.md)
