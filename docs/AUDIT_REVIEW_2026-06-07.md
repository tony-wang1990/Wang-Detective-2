# Wang-Detective 第三方审计复核记录

复核对象：`Wang-Detective 全量 Bug 审计报告.doc`

复核日期：2026-06-07

## 结论

这份审计报告有一部分判断是成立的，尤其集中在鉴权边界、敏感信息存储、前端 token 暴露、流式请求释放和操作审计。也有一部分属于旧版本问题、误判，或如果直接照做会破坏当前项目的真实使用场景。

## 已采纳并修复

| 项目 | 处理结果 |
| --- | --- |
| `/chat/**` AI 接口未纳入鉴权 | 已纳入 `AuthInterceptor` 鉴权范围，前端请求补齐 Authorization。 |
| 管理员密码明文存储 | 新增 PBKDF2 哈希存储，旧明文密码在登录成功后自动迁移。 |
| JWT 签名密钥复用管理员密码 | 新增独立持久化 token secret，不再使用管理员密码签发 token。 |
| `/metrics/{token}` 在路径中暴露 token | 改为 `/metrics?token=...`，WebSocket 服务端同步校验 query token。 |
| 首页地图 popup 存在 HTML 注入风险 | Leaflet popup 字段增加 HTML 转义。 |
| AI 流式请求切页后未取消 | 前端增加 `AbortController`，组件卸载和清空会话时主动终止请求。 |
| AI 会话历史原地修改可能互相污染 | 后端改为复制历史、完成后裁剪并写回缓存。 |
| 联网搜索资料可能触发提示词注入 | 搜索结果作为“不可信参考资料”注入提示，禁止执行资料中的指令。 |
| 系统配置敏感字段明文输入 | Token、Secret、AI Key、MFA Secret 等改为密码输入。 |
| 系统配置保存携带只读/展示字段 | 前端保存时只提交后端需要的配置字段。 |
| 修改管理员账号密码缺少前端校验 | 增加当前密码、新账号、新密码长度和二次确认校验。 |
| 路由守卫只看当前路由 meta | 改为检查 `to.matched`，子路由继承鉴权要求。 |
| 登录页默认填充 admin | 改为空输入，减少默认账号泄露和误操作。 |
| MFA 状态读取失败时默认放行 | 改为读取失败即阻止登录并提示错误。 |
| 401 重定向重复触发 | 统一 401 处理，避免多次弹跳。 |
| 健康检查携带 Authorization | `/actuator/health` 不再发送登录 token。 |
| 安全规则新增/删除缺少审计 | `addIngress`、`addEgress`、`remove` 增加成功/失败审计日志。 |
| 恢复接口参数绑定不明确 | 显式标注 `@ModelAttribute`，保持 multipart 文件恢复能力。 |
| 前端 dist 旧哈希文件累积 | Vite 输出改为清理 dist 后再构建，仓库只保留当前产物。 |

## 未直接采纳的点

| 报告观点 | 复核判断 |
| --- | --- |
| Web SSH / 日志 WebSocket 完全无鉴权 | 当前实现已在各自 WebSocket handler 内校验 token；成立的是 metrics token 位置不合理，已修复。 |
| SFTP 必须禁止所有 `..` 路径 | 不直接采纳。SFTP 是远程文件管理器，目录上级导航是核心能力；应做危险操作确认和路径展示，不应粗暴禁用。 |
| 备份恢复接口应改成 `@RequestBody` | 不采纳。恢复接口包含 `MultipartFile`，应走 multipart 表单；`@RequestBody` 会破坏上传恢复。 |
| 救援中心应一键执行所有 netboot/拆卷操作 | 暂不采纳为默认自动执行。救援和 netboot 属于高风险动作，当前保留脚本和复制入口更安全；后续如做一键执行，需要实例选择、二次确认、回滚点和操作审计齐全。 |
| 风险看板只分析端口没有意义 | 当前风险看板已包含实例运行、ARM 用量、引导卷容量、安全列表公网暴露等；端口不是唯一指标，但安全规则仍是最直接的公网风险信号。 |

## 本轮验证

- 已通过 `npm --prefix frontend run build`，前端源码和 `src/main/resources/dist` 打包产物已同步。
- 本地环境没有可用 `java`、`mvn` 或 Docker，后端完整 Maven 编译需交给 GitHub Actions 或服务器环境验证。

