# 运维脚本工具箱

本目录记录服务器侧脚本的用途和调用方式。原则是：先把部署、备份、恢复、更新、回滚、日志采集和发布验证做成稳定工具；最后再集中处理功能 BUG 和完整验收。

所有脚本默认应用目录为 `/app/king-detective`，可通过 `APP_DIR` 覆盖。

## 脚本总览

| 脚本 | 用途 | 是否修改数据 |
| --- | --- | --- |
| `scripts/install.sh` | 一键安装/刷新 compose、配置和运维脚本 | 会创建/更新部署文件，不覆盖 `.env` |
| `scripts/server-smoke-test.sh` | 部署后体检，检查容器、登录、健康、watcher、关键 API | 只读 |
| `scripts/remote-smoke-test.sh` | 公网/域名远程验收，检查健康、登录、诊断、风险、VCN 和安全规则读接口 | 只读 |
| `scripts/remote-smoke-test.mjs` | Node 版远程验收，支持 fetch/curl 自动回退 | 只读 |
| `scripts/backup.sh` | 备份 `.env`、配置、数据库、keys、scripts | 只创建备份 |
| `scripts/restore.sh` | 从备份恢复配置、数据库、keys、scripts | 会覆盖当前部署文件 |
| `scripts/update.sh` | 手动拉取镜像并重建服务 | 会重建容器 |
| `scripts/rollback.sh` | 切换到指定镜像并重建服务 | 会重建容器 |
| `scripts/support-bundle.sh` | 采集脱敏支持包，便于排错 | 只读 |
| `scripts/maintenance.sh` | 统一维护入口，封装常用动作 | 取决于选择的动作 |
| `scripts/setup-backup-cron.sh` | 安装/删除定时备份计划 | 会写 `/etc/cron.d` |
| `scripts/verify-release.sh` | 发布前轻量验证，能跑什么就跑什么 | 只读/构建产物 |
| `scripts/watcher.sh` | Web/TGBOT 一键更新 watcher | 会拉镜像并重建应用容器 |

## 一键安装后脚本同步

新版 `install.sh` 会同步以下脚本到服务器：

```bash
/app/king-detective/scripts/
```

因此部署后可以直接执行：

```bash
cd /app/king-detective
bash scripts/maintenance.sh menu
```

## 备份

创建本地备份：

```bash
cd /app/king-detective
bash scripts/backup.sh
```

默认备份内容：

- `.env`
- `application.yml`
- `docker-compose.yml`
- `data/`
- `keys/`
- `scripts/`
- 容器和镜像元信息

默认不打包日志，避免备份过大。如需包含日志：

```bash
INCLUDE_LOGS=1 bash scripts/backup.sh
```

默认保留 14 天备份。如需调整：

```bash
RETENTION_DAYS=30 bash scripts/backup.sh
```

## 恢复

恢复会覆盖当前 `.env`、配置、数据库、keys 和 scripts。脚本会先尝试创建恢复前备份，并把旧文件移动到 `.restore-archive-*`。

先只校验备份包，不恢复：

```bash
RESTORE_VERIFY_ONLY=1 bash scripts/restore.sh /app/king-detective/backups/wang-detective-backup-YYYYmmdd-HHMMSS.tar.gz
```

也可以通过统一维护入口校验：

```bash
bash scripts/maintenance.sh verify-backup /app/king-detective/backups/wang-detective-backup-YYYYmmdd-HHMMSS.tar.gz
```

```bash
cd /app/king-detective
bash scripts/restore.sh /app/king-detective/backups/wang-detective-backup-YYYYmmdd-HHMMSS.tar.gz
```

非交互环境可以显式确认：

```bash
RESTORE_CONFIRM=YES bash scripts/restore.sh /path/to/backup.tar.gz
```

## 手动更新

```bash
cd /app/king-detective
bash scripts/update.sh
```

默认会先备份，再拉取：

```text
ghcr.io/tony-wang1990/wang-detective:main
```

指定镜像：

```bash
KING_DETECTIVE_IMAGE=ghcr.io/tony-wang1990/wang-detective:main bash scripts/update.sh
```

更新后如需自动跑体检：

```bash
RUN_SMOKE_AFTER_UPDATE=1 bash scripts/update.sh
```

更新成功后会写入：

```text
runtime/last_successful_update
runtime/last_image_before_update
```

## 回滚

回滚到指定镜像：

```bash
cd /app/king-detective
bash scripts/rollback.sh ghcr.io/tony-wang1990/wang-detective:main
```

如果刚刚通过 `scripts/update.sh` 更新过，脚本会记录更新前镜像到：

```text
runtime/last_image_before_update
```

没有传入镜像时，`rollback.sh` 会优先尝试读取这个文件。

回滚后如需自动跑体检：

```bash
RUN_SMOKE_AFTER_ROLLBACK=1 bash scripts/rollback.sh ghcr.io/tony-wang1990/wang-detective:main
```

回滚成功后会写入：

```text
runtime/last_successful_rollback
runtime/last_image_before_rollback
```

## 支持包

生成排错支持包：

```bash
cd /app/king-detective
bash scripts/support-bundle.sh
```

支持包会采集：

- 脱敏 `.env`
- 脱敏 `application.yml`
- 脱敏 `docker-compose.yml`
- `docker ps`
- `docker inspect`
- 最近应用日志
- 最近 watcher 日志
- 健康检查返回
- 磁盘和内存信息

输出目录：

```text
/app/king-detective/support-bundles/
```

脚本会尽量脱敏，但发送给他人前仍建议人工检查。

## 定时备份

安装每日备份计划，默认每天 03:17 执行：

```bash
cd /app/king-detective
bash scripts/setup-backup-cron.sh install
```

自定义时间：

```bash
CRON_SCHEDULE="12 4 * * *" bash scripts/setup-backup-cron.sh install
```

查看：

```bash
bash scripts/setup-backup-cron.sh show
```

删除：

```bash
bash scripts/setup-backup-cron.sh remove
```

## 维护入口

交互菜单：

```bash
bash scripts/maintenance.sh menu
```

非交互命令：

```bash
bash scripts/maintenance.sh status
bash scripts/maintenance.sh health
bash scripts/maintenance.sh backup
bash scripts/maintenance.sh verify-backup /path/to/backup.tar.gz
bash scripts/maintenance.sh update
bash scripts/maintenance.sh rollback ghcr.io/tony-wang1990/wang-detective:main
bash scripts/maintenance.sh smoke
bash scripts/maintenance.sh support
```

## 发布前轻量验证

本地或 CI 可执行：

```bash
bash scripts/verify-release.sh
```

脚本会根据当前环境自动执行：

- `git diff --check`
- `bash -n scripts/*.sh`
- `npm --prefix frontend run build`
- `mvn -DskipTests package`

如果环境没有 `npm` 或 `mvn`，会提示跳过，不会伪造验证结果。

## 远程线上验收

部署完成并确认公网或域名可访问后，执行：

```bash
cd /app/king-detective
bash scripts/remote-smoke-test.sh https://your-domain.example admin 'your-password'
```

这条命令只做安全读检查，不会启动、停止、删除或修改 OCI 资源。当前覆盖：

- 健康检查、登录、系统诊断、版本信息。
- 旧地图、旧功能中心、旧运维终端入口是否已经迁移到 Vue 原生路由。
- 首页概览、OCI 配置分页、任务分页、最近审计。
- 本地备份、救援中心、风险看板。
- VCN 分页和入站/出站安全规则只读明细。

本机有 Node 20+ 时可使用 JS 版；默认会在 `fetch` 不稳定时自动回退到 `curl`：

```bash
node scripts/remote-smoke-test.mjs \
  --base https://your-domain.example \
  --username admin \
  --password 'your-password' \
  --transport auto \
  --timeout 60000
```

## 后续脚本方向

后续功能完成后，脚本层还需要继续补：

- Object Storage 备份归档脚本：把数据库、配置和日志备份上传到 OCI Bucket。
- 日志清理脚本：按天数清理本地日志和支持包。
- 多实例批量体检脚本：对多台部署机器批量跑 `server-smoke-test.sh`。
- 安全巡检脚本：检查默认密码、公开端口、文件权限、敏感环境变量和过期备份。
