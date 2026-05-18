# Wang-Detective 增强报告

## 调研结论

对比 R-Bot / java_oci_manage 一类 OCI 运维项目后，高价值能力主要集中在四类：

- 运维入口：Web SSH、SFTP、批量命令、端口转发、主机同步。
- 云资源闭环：实例、网络、卷、DNS、对象存储、Email Delivery。
- 风险看板：Always Free 配额、成本、流量、启动失败原因和容量不足重试。
- 安全边界：私钥本地保存、登录保护、MFA、操作审计、更新可回滚。

当前版本先完成影响部署稳定性的修复，并落地第一优先级的运维入口一期能力。没有直接复制同类项目代码，只吸收产品方向后结合本仓库结构实现。

## 已完成修复

### 部署与更新

- `docker-compose.yml` 不再把整个当前目录挂载到 `/app/king-detective`，避免覆盖镜像内 JAR。
- 数据库、私钥、日志、运行时文件分别挂载到 `data/`、`keys/`、`logs/`、`runtime/`。
- `Dockerfile` 增加健康检查并统一 `KING_DETECTIVE_VERSION`。
- `scripts/install.sh` 生成 `.env`，不再下载仓库中不存在的数据库文件。
- `scripts/watcher.sh` 接受任意非空触发内容，修复 DB 路径和 `oci_kv.id` 插入。
- `scripts/update.sh` 提供手动更新入口，兼容新版 `KING_DETECTIVE_IMAGE` 和更新前备份。

### 数据库

- `DatabaseMigrationRunner` 会先移除 SQL 注释行再拆分执行，避免迁移语句被整段跳过。
- `migration_v4_0.sql` 移除不存在字段 `oci_create_task.status` 的索引。
- 启用 MyBatis Plus SQLite 分页拦截器。

### 运行稳定性

- 修复 `CHANGE_IP_TASK_PREFIX` 与 `CREATE_TASK_PREFIX` 相同的问题。
- 修复 VCN、引导卷分页 total 只返回当前页数量的问题。
- WebSocket 日志处理改用 Spring 管理的 `LogWebSocketHandler`，支持 `@Value` 注入。
- WebSocket 非法 token 会主动关闭连接，历史日志推送条件判断修复。
- JWT 过期判断对畸形 token 返回过期，避免异常冒泡成 500。
- OCI API 重试切面尊重 `@RetryableOciApi(maxAttempts, delayMs)` 参数。
- API 限流异常使用标准 HTTP 429。

### 安全与可观测

- CORS 来源支持 `CORS_ALLOWED_ORIGINS` 配置。
- `/actuator/health` 增加版本、运行时长、JVM 内存和数据库状态。
- 新增 `GET /api/v1/system/diagnostics`，输出数据库、目录、日志、默认密码、Telegram 配置、磁盘和运行时状态。
- README、FEATURES、API 文档和增强报告重新整理为清晰中文。

## 第一优先级已落地：运维入口一期

### 新增页面

- `src/main/resources/static/ops-terminal.html`
- 访问路径：`/ops-terminal.html`

页面支持登录 token 自动读取，也支持手动粘贴 token。无需先改现有前端打包产物，能以最小风险提供可用入口。

### 新增后端

- `OpsSshController`：提供 SSH/SFTP REST API。
- `WebSshService`：封装 JSch SSH exec、SFTP list/read/write/upload/download/mkdir/delete/rename。
- `WebSshSessionRegistry`：短时保存 Web SSH 会话凭据，过期自动清理。
- `SshTerminalWebSocketHandler`：提供交互式 SSH Shell WebSocket。
- `SshHostService`：提供 SSH 主机资产库、AES-GCM 凭据加密和 `hostId` 凭据复用。
- `AuditLogServiceImpl`：补齐审计日志写入能力，运维入口的高风险操作会写入 `audit_log`。

### 已提供能力

- SSH 连接测试。
- Web SSH 交互式终端。
- SSH 单命令执行，返回 stdout、stderr、退出码、耗时、是否超时。
- 多主机批量执行同一命令。
- SFTP 目录列表、读取 1MB 以内文本、写入文本、上传文件、下载文件、创建目录、删除、递归删除、重命名。
- 保存常用 SSH 主机，前端可直接选择主机执行终端、命令和 SFTP 操作。
- 新增 `ops_ssh_host` 表和 `db/migration_v4_1_ops.sql`，已有部署会自动补齐主机资产表。
- 新增 `GET /api/ops/audit/recent`，用于查看最近运维操作审计记录。

## 下一阶段建议

1. 运维入口二期：权限控制、操作审计、端口转发、大文件传输进度和断点续传。
2. OCI Object Storage：Bucket/Object 管理、数据库备份归档、日志归档、临时下载链接。
3. OCI Email Delivery：自动生成 DKIM/SPF 指引、SMTP 凭据检查和测试发信。
4. Always Free 风险看板：汇总 A1 OCPU/内存、块存储、实例数、流量和可能计费项。
5. 配额与容量失败分析：按区域记录 `InsufficientHostCapacity`、`LimitExceeded`，给出重试建议。
6. 审计页面：把已有 `audit_log` 表真正接入查询、导出和筛选。
