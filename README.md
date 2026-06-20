# Wang-Detective

Wang-Detective 是面向 Oracle Cloud Infrastructure (OCI) 的 Web 管理面板与 Telegram Bot 运维助手，覆盖 OCI 配置、实例、任务、网络、安全规则、引导卷、Web SSH/SFTP、风险诊断、备份恢复和救援操作。

当前状态：**功能开发基本完成，进入稳定性与真实高危操作验收阶段。**

状态更新时间：2026-06-20

## 界面预览

![Wang-Detective 首页](docs/images/wang-detective-home.png)

![风险看板](docs/images/wang-detective-risk.png)

## 快速部署

```bash
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```

部署后：

- Web：`http://服务器IP:9527`
- 健康检查：`http://服务器IP:9527/actuator/health`
- 默认账号来自 `/app/king-detective/.env`

建议立即修改：

```env
ADMIN_USERNAME=admin
ADMIN_PASSWORD=请改为强密码
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_CHAT_ID=
```

## 当前完成度

| 模块 | 状态 | 说明 |
|---|---|---|
| Docker 部署与更新 | 已完成 | Compose v2、watcher、一键更新、健康等待、低配 JVM、持久化目录与运维脚本已接入 |
| Vue 前端 | 已完成 | 登录、首页、配置、任务、风险、备份、救援、终端、日志、审计、系统配置等均为原生路由 |
| OCI 核心管理 | 已完成待高危实测 | 配置、实例、电源、网络、安全规则、引导卷、Shape、IPv6、换 IP、500M 等调用真实 OCI SDK |
| Telegram Bot | 已完成待线上复验 | 114 个按钮 callback 对应 155 个处理器模式；异常返回带功能名和错误编号 |
| Web SSH/SFTP | 已完成 | 主机库、会话、重连、resize、命令模板、上传下载及危险操作确认已接入 |
| 备份恢复 | 已完成待恢复演练 | 本地备份、Object Storage、Web 恢复、定时备份与 watcher 执行链已完成 |
| 救援中心 | 已完成待专用机验收 | 一键自动救援、轻量检查、boot volume 救援与 netboot 实验入口已接入 |
| 安全与审计 | 已完成 | Token、MFA、登录防爆破、IP 黑名单、管理员凭据修改和高危操作审计已覆盖 |
| CI/自动验收 | 已完成 | Java 21 测试、Vue 构建、脚本检查、前后端 API 映射和 TG callback 映射已接入 |

## 本轮全量审计修复

- 未知 `/api/*` 不再错误返回 Vue `index.html` 和 HTTP 200，而是返回 JSON 404。
- `/actuator/*` 不再被防御模式或黑名单误伤，避免 Docker 健康检查假失败。
- 管理员账号或密码修改后自动轮换 JWT 密钥，旧登录 Token 立即失效。
- 登录失败不再逐次向 TG 推送，避免扫描攻击造成通知轰炸。
- 只信任来自本机或私网反向代理的 `X-Forwarded-For`，防止公网直连伪造来源 IP。
- 登录失败记录与 IP 黑名单增加重复数据清理、唯一索引和并发保护。
- 日志 WebSocket 支持多个浏览器会话，不再出现新页面挤掉旧页面。
- VNC 隧道只终止本项目自己启动的旧进程，不再粗暴杀死占用 5900 端口的其他程序。
- OCI VNIC 与引导卷列表修复空值和非线程安全聚合问题。
- GitHub 版本查询与启动通知移出 Spring 启动关键路径。
- 安全规则、VCN、租户、Cloudflare 等接口收紧为明确 POST。
- 备份、引导卷、VCN、租户、Cloudflare 等高危操作补充操作审计。
- TG callback 异常返回功能名和错误编号，服务日志可按编号定位真实异常。
- TG callback 校验增加空模式、重复模式和 Spring 实例化回归测试。
- 删除使用价值较低的 AI 聊天页面、接口、TG 菜单、配置项和 Spring AI 依赖，普通 TG 文本改为引导使用明确的运维菜单。

完整报告：[docs/CODE_AUDIT_REPORT.md](docs/CODE_AUDIT_REPORT.md)

## 验证结果

- Java 21 / Maven：10 项测试通过。
- Vue：TypeScript 检查与生产构建通过。
- 后端 Controller：135 个端点。
- 前端 API：72 个调用，全部存在对应后端映射。
- Telegram：114 个按钮 callback，155 个处理器模式，映射检查通过。
- 静态验收：脚本、路由、乱码、README 链接、低配健康检查和发布守卫全部通过。

## 尚需真实环境验收

以下操作代码链已接通，但不能在生产资源上自动做破坏性测试：

1. 终止实例、删除 VCN、删除安全规则和终止引导卷。
2. boot volume 拆卷救援、自动救援和恢复回滚。
3. Object Storage 真实上传、下载、删除及完整恢复演练。
4. netboot.xyz 在不同 AMD/ARM、UEFI/BIOS 系统上的一次性引导。
5. 1C1G 服务器部署后的最终启动时间和 TG 全菜单线上复验。

## 常用命令

```bash
cd /app/king-detective

docker compose ps
docker logs -f king-detective
bash scripts/server-smoke-test.sh

# 线上只读验收
bash scripts/remote-smoke-test.sh https://你的域名 管理员账号 '管理员密码'

# 备份与支持包
bash scripts/backup.sh
bash scripts/support-bundle.sh
```

## 文档

- [全量代码审计报告](docs/CODE_AUDIT_REPORT.md)
- [部署验收](docs/DEPLOYMENT_SMOKE_TEST.md)
- [验收矩阵](docs/ACCEPTANCE_MATRIX.md)
- [启动性能审计](docs/STARTUP_PERFORMANCE_AUDIT_2026-06-19.md)
- [救援与 netboot 路线](docs/RESCUE_NETBOOT_ROADMAP.md)
- [安全策略](SECURITY.md)
