# 部署冒烟检查与真实 OCI 验收清单

这份文档用于服务器部署后快速判断新版是否真正可用。脚本只做安全读检查和登录/API 连通性检查，不会启动、停止、删除或修改 OCI 资源；涉及真实 OCI 变更的动作放在后面的手工清单里逐项确认。

## 一键冒烟检查

部署完成后，在服务器 SSH 中执行：

```bash
cd /app/king-detective
bash scripts/server-smoke-test.sh
```

如果本地目录里还没有脚本，可以直接拉取最新版本运行：

```bash
cd /app/king-detective
wget -qO scripts/server-smoke-test.sh https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/server-smoke-test.sh
chmod +x scripts/server-smoke-test.sh
bash scripts/server-smoke-test.sh
```

也可以用单行命令：

```bash
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/server-smoke-test.sh)
```

脚本默认检查 `http://127.0.0.1:9527`。如果部署目录或端口不同，可以指定：

```bash
APP_DIR=/app/king-detective BASE_URL=http://127.0.0.1:9527 bash scripts/server-smoke-test.sh
```

如果想把真实 OCI 详情接口也纳入检查，先在配置列表里找到一个配置 ID，然后执行：

```bash
OCI_CFG_ID=1 bash scripts/server-smoke-test.sh
```

## 远程线上验收

如果要从服务器或另一台机器验证公网域名、反向代理、Cloudflare 和后端 API 是否整体可用，使用远程验收脚本：

```bash
cd /app/king-detective
bash scripts/remote-smoke-test.sh https://your-domain.example admin 'your-password'
```

脚本也支持环境变量，避免密码进入 shell 历史：

```bash
cd /app/king-detective
WANG_DETECTIVE_BASE_URL=https://your-domain.example \
WANG_DETECTIVE_USERNAME=admin \
WANG_DETECTIVE_PASSWORD='your-password' \
bash scripts/remote-smoke-test.sh
```

当前远程脚本会检查 17 个安全读接口：

| 检查项 | 说明 |
| --- | --- |
| `health` | `/actuator/health` 必须返回 `UP` |
| `legacy-map-redirect` | 旧地图入口 `/ip-map.html` 必须指向新版首页 |
| `legacy-features-redirect` | 旧功能中心入口 `/wang-features.html` 必须指向 Vue 功能中心 |
| `legacy-terminal-redirect` | 旧运维终端入口 `/ops-terminal.html` 必须指向 Vue 运维终端 |
| `login` | 使用真实账号登录并提取 Bearer token |
| `diagnostics` | 系统诊断接口返回检查项 |
| `version-info` | 版本检查接口返回当前版本 |
| `glance` | 首页资源概览接口可访问 |
| `oci-user-page` | OCI 配置分页可访问 |
| `task-page` | 任务分页可访问 |
| `audit-recent` | 最近操作审计可访问 |
| `backup-local` | 本地备份列表可访问 |
| `rescue-overview` | 救援中心概览可访问 |
| `oci-risk` | 风险看板接口可访问，并返回风险配置结构 |
| `vcn-page` | 根据第一个 OCI 配置读取 VCN 分页 |
| `security-rules-ingress` | 根据第一个 VCN 读取入站安全规则 |
| `security-rules-egress` | 根据第一个 VCN 读取出站安全规则 |

如果服务器安装了 Node 20+，也可以使用 JS 版。JS 版默认 `--transport auto`，当 Node fetch 直连 Cloudflare 超时时会自动回退到 `curl`：

```bash
node scripts/remote-smoke-test.mjs \
  --base https://your-domain.example \
  --username admin \
  --password 'your-password' \
  --timeout 60000
```

也可以强制使用 curl：

```bash
node scripts/remote-smoke-test.mjs \
  --base https://your-domain.example \
  --username admin \
  --password 'your-password' \
  --transport curl \
  --timeout 60000
```

## 脚本检查内容

| 检查项 | 说明 |
| --- | --- |
| 基础命令 | 检查 `docker`、`curl`、`grep`、`sed`、`date` 是否可用 |
| 部署文件 | 检查 `.env`、`docker-compose.yml`、`application.yml` 是否存在 |
| Compose 配置 | 检查 GHCR 镜像、watcher 服务和 `docker compose config` |
| 容器状态 | 检查 `king-detective` 和 `king-detective-watcher` 是否运行 |
| watcher 心跳 | 检查 `runtime/watcher_heartbeat` 是否持续更新 |
| 健康检查 | 检查 `/actuator/health` 是否返回 `UP` 和版本号 |
| 前端页面 | 检查 `/login`、首页、配置页、功能中心、运维终端是否能返回 Vue 页面 |
| 登录链路 | 使用 `.env` 中管理员账号登录，确认 token 正常返回 |
| 关键 API | 检查系统诊断、版本信息、首页概览、配置分页、任务分页、运维主机、操作审计 |
| 日志 | 扫描最近应用和 watcher 日志中的启动级错误 |
| 主机资源 | 检查磁盘和可用内存，提示低配 VPS 风险 |

脚本结果中：

- `PASS`：当前检查通过。
- `WARN`：功能可继续用，但建议处理，例如默认密码、Bot 未配置、内存偏低。
- `FAIL`：会影响部署或核心功能，脚本会以非 0 状态退出。

## 真实 OCI 操作验收清单

这些项目会真实访问或修改 OCI 资源，建议先在测试租户或可丢弃实例上验证。

| 模块 | 验收动作 | 期望结果 | 风险 |
| --- | --- | --- | --- |
| 登录 | 打开 `/login` 登录 | 登录按钮有 loading，成功后进入控制台 | 低 |
| 首页 | 查看地图、健康状态、版本号、资源图表 | 数据来自健康检查、概览接口和 WebSocket，不应长期空白 | 低 |
| 配置列表 | 查询、筛选、分页 OCI 配置 | 表格响应正常，按钮不挤字 | 低 |
| 配置列表 | 打开某个配置的实时详情 | 调用 OCI SDK 返回实例、NLB、Cloudflare 等真实数据 | 低 |
| 配置列表 | 测活 | OCI 凭据有效时返回可用状态，错误时显示明确原因 | 低 |
| 实例操作 | 启动、停止、重启测试实例 | OCI 控制台状态同步变化，页面提示成功或失败 | 中 |
| 实例操作 | 修改实例名称 | OCI 实例名称更新，审计中记录操作 | 中 |
| 实例操作 | 换 IP / 停止换 IP | 后端创建或停止换 IP 任务，任务列表可见状态 | 中 |
| 网络操作 | 创建 IPv6 | 测试 VNIC 分配 IPv6 成功，失败时返回 OCI 错误 | 中 |
| VNC/救援 | 启动 VNC、自动救援 | 仅在测试实例上验证，观察日志和 OCI 状态 | 中 |
| 性能配置 | 修改 Shape、CPU、内存、500M 开关 | OCI 返回成功，实例配置符合预期 | 高 |
| 引导卷 | 修改引导卷大小或性能 | 卷配置变化，失败时提示限制原因 | 高 |
| 终止实例 | 发送验证码，不建议直接终止生产实例 | 验证验证码流程即可，除非是可删除测试机 | 高 |
| 任务列表 | 停止单个任务、批量停止任务 | 页面弹窗确认，任务状态更新 | 中 |
| 服务日志 | 搜索、筛选、刷新最近日志 | 日志来自后端文件/容器，不是静态假数据 | 低 |
| 系统配置 | 保存 Telegram、钉钉、日报、自动更新配置 | 保存成功后诊断状态同步变化 | 中 |
| 运维终端 | 保存 SSH 主机、测试连接、打开 Web SSH | 终端连接真实主机，resize 和断线重连正常 | 中 |
| SFTP | 列目录、上传、下载、重命名、删除测试文件 | 文件真实变化，删除需要二次确认 | 中 |
| 操作审计 | 查看最近操作审计 | Web 和运维动作能留下审计流水 | 低 |
| Telegram Bot | `/start` 打开菜单 | 能看到运维中心、诊断、任务、日志、更新入口 | 低 |
| Telegram Bot | 点击系统诊断、任务状态、最近日志 | 返回真实后端数据 | 低 |
| Telegram Bot | 点击版本检查和更新 | watcher 在线时能触发更新，失败时给出明确原因 | 中 |

## 常见失败处理

### 服务启动慢

1C/1G VPS 首次启动需要 40 到 90 秒属于正常范围。安装脚本会等待 `/actuator/health` 变为 `UP`，如果超过 15 分钟仍失败，再看日志：

```bash
docker logs --tail 200 king-detective
docker inspect --format '{{json .State}}' king-detective
```

### 一键更新失败

先确认 watcher 在：

```bash
docker ps --filter name=king-detective
docker logs --tail 100 king-detective-watcher
ls -l /app/king-detective/runtime/watcher_heartbeat
```

如果 watcher 缺失，重新运行安装脚本即可：

```bash
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```

### 公网 IP 正常但域名 504

如果 `http://服务器IP:9527/actuator/health` 正常，但 `https://域名/actuator/health` 返回 502/504，说明应用本身大概率已经启动，问题通常在 Nginx Proxy Manager、Cloudflare 或源站回源链路：

```bash
curl -i http://127.0.0.1:9527/actuator/health
curl -i http://服务器IP:9527/actuator/health
curl -i https://你的域名/actuator/health
docker logs --tail 100 npm_app
```

重点检查：

- Nginx Proxy Manager 的 Forward Host/IP 是否指向服务器内网或公网可达地址。
- Forward Port 是否为 `9527`。
- Cloudflare 是否启用了代理，以及 DNS 记录是否指向当前服务器 IP。
- 防火墙或安全组是否放行 80/443 和 9527。

### Telegram 已配置但仍提示未配置

检查 `.env` 至少包含：

```env
TELEGRAM_BOT_TOKEN=your_token
TELEGRAM_BOT_CHAT_ID=your_chat_id
TELEGRAM_CHAT_ID=your_chat_id
```

然后重启：

```bash
docker compose up -d --force-recreate
```

### 回退

如果新版启动失败，可以先保留数据目录，只重建容器：

```bash
cd /app/king-detective
docker compose pull
docker compose up -d --force-recreate
```

如果是 `docker-compose.yml` 被旧版本污染，安装脚本会自动备份并刷新。也可以手动删除 compose 后重新安装：

```bash
cd /app/king-detective
cp docker-compose.yml docker-compose.yml.bak.$(date +%Y%m%d%H%M%S)
rm -f docker-compose.yml
bash <(wget -qO- https://raw.githubusercontent.com/tony-wang1990/Wang-Detective/main/scripts/install.sh)
```
