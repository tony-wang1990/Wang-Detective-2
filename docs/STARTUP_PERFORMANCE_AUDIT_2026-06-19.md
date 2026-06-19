# 启动性能审计（2026-06-19）

## 结论

本次 707 秒启动不是单一代码死循环，也不是 OCI API 在主启动线程中等待。
直接原因是 1C/1G 主机长期处于 CPU 100%、高系统负载和频繁换页状态；
代码侧原本又在 Spring 主启动链路中一次性创建 Telegram 回调和 AI 可选模块，
两者叠加后把正常的 Bean 初始化时间放大到十几分钟。

## 服务器日志时间线

| 阶段 | 时间 | 耗时特征 |
| --- | --- | --- |
| Java 进程启动 | 23:12:37 | 起点 |
| Tomcat 初始化 | 23:16:36 | 前置扫描约 4 分钟 |
| WebApplicationContext 完成 | 23:17:02 | 上下文初始化 255 秒 |
| Hikari 开始/完成 | 23:18:47 / 23:18:51 | 数据库连接本身约 4 秒 |
| Telegram 回调加载完成 | 23:23:00 | 大量 Bean 在资源紧张时被放大 |
| Tomcat 启动 | 23:24:14 | 后续 Bean 初始化约 74 秒 |
| 应用启动完成 | 23:24:15 | 总计 707.671 秒 |

同期服务器截图显示 CPU 100%、内存约 93%、Swap 约 65%，并且 `kswapd0`、
监控 agent、Nezha 和 Docker daemon 同时争用 CPU。Java 并不是当时唯一或最大的 CPU 消耗者。

## 代码审计结果

- 当前与 2026-05-26 的快速版本使用同一套主要依赖，Telegram handler 文件数量没有显著增长。
- 未发现启动线程中的固定长时间 `sleep`。
- OCI 实时网络请求主要在后台任务、用户操作或定时任务中执行，不阻塞 Tomcat 主初始化。
- `CallbackHandlerFactory` 原来会促使约 160 个 Telegram 回调 Bean 在启动期创建。
- AI 功能手工创建 `ChatClient`，却仍引入 Spring Boot AI Starter，产生了不必要的自动配置。
- 数据库连接建立约 4 秒，不是这次 707 秒的主因。

## 本次优化

- Telegram handler、回调工厂和 AI 模块改为延迟初始化。
- Web 核心服务就绪后，默认延迟 20 秒在后台预热 Telegram handler 并启动 Bot。
- `spring-ai-openai-spring-boot-starter` 改为轻量的 `spring-ai-openai` 客户端依赖。
- 新增启动性能日志，记录启动秒数、可用处理器、系统负载和 JVM 堆使用量。
- 统一 1C/1G JVM 默认值，并使用 Tier 1 编译降低低配机启动期 JIT 压力。
- 安装等待超过 120 秒时自动输出负载、内存、进程和容器资源诊断。

## 本地验证

使用 Java 21、单可用处理器、SerialGC、384MB 最大堆：

| 构建 | 核心服务就绪 |
| --- | --- |
| 优化前 `5fc620f` | 约 7.8 秒 |
| 优化后 | 约 7.6 秒 |
| 优化后 + `TieredStopAtLevel=1` | 约 7.4 秒 |

空闲机器上的差异不大，说明代码没有固定的数分钟阻塞；优化的主要价值是在低配和高负载环境下，
避免可选模块与 Web 核心服务争抢启动时间。

## 服务器验收标准

部署后日志应先出现 `Started KingDetectiveApplication`，随后再出现：

```text
TG Bot startup scheduled 20 seconds after the web application is available
TG callback handlers warmed in ... ms
TG Bot successfully started ...
```

如果 `Startup performance` 显示系统负载明显高于可用 CPU，或安装脚本报告可用内存低于 150MB，
应先停止重复监控 agent 或增加实例资源，再判断应用性能。
