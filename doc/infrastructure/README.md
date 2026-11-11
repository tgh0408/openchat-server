# 基础设施与集群

[文档首页](../README.md) · [MQ 与可靠调用](messaging.md)

## DAO 与缓存

业务层使用现有 DAO 和缓存入口。用户、群资料、成员、好友、房间及媒体等读取按各自缓存职责执行，不能从业务侧添加“最新查询”旁路来绕开一致性逻辑。

`LockedRedisCache` 的主要行为：

- 每个键独立加载和失效，数据默认保留 300 秒。
- 命中从 Redis 主节点读取；未命中在既有分布式锁协调下回源。
- 回填和删除通过 Lua 同时核对锁持有者，失去归属则失败。
- 缓存键包含 Redis Cluster hash tag，使锁键和数据键参与同槽操作。
- 数据库事务内由缓存组件回源，不向共享缓存写入未提交数据或事务快照。
- 写入后的共享缓存失效与事务提交协调。

缓存是读取入口，不是数据库并发保障。唯一约束、`@Version` 或带条件的原子更新仍负责写入冲突判断，且要检查受影响行数。

当前实现使用的 Redis 锁有明确的归属与释放机制；它们不能用来证明数据库迟到写入天然安全，也不是新增任意锁的依据。

## 房间活跃合并写

消息消费将最新消息 ID 和时间记录到 Redis，定时刷新 `room.last_msg_id`、`room.active_time`。该流程不合并消息正文、逐条推送或已读确认。

| 项目 | 值 |
| --- | --- |
| 合并窗口 | 默认 1000ms |
| 刷新 fixedDelay | 默认 500ms |
| 每轮批量 | 默认 500 个房间 |
| 数据 | 每房间 Hash 快照与到期 ZSet |

Lua 按十进制字符串比较消息 ID，使用 Redis 时间安排到期；同一窗口新消息不会无限延后首次落库。数据库写入保持单调，缓存失效成功后再确认快照。

确认脚本只移除已经处理的版本，期间到达的新消息继续保留。失败待办不能当作普通缓存清空。多个实例使用现有刷新锁协调处理，锁失效时停止后续工作；最终写入仍依赖数据库条件。

Redis 持久化、复制和容量策略会影响待办恢复能力。会话排序、摘要及历史上界存在最终一致性延迟，不能把它描述为消息到达瞬间全部可见。

## 对象存储

OSS 模块提供 MinIO 自动配置和模板，聊天业务负责上传场景、用户归属、消息状态及下载授权。

- `endpoint` 供服务端操作；`upload-endpoint` 供客户端签名请求；`public-endpoint` 供公开资源地址。
- 上传会话保存在 Redis，文件和视频分片完成后仍需完成消息占位。
- 桶必须与环境配置一致，代理不能修改签名 Host、路径或参数。
- 业务异常使用统一响应，底层诊断经脱敏后写日志，不输出访问密钥、签名 URL 或请求头全文。
- 对象存储和数据库分步完成。资源清理由可靠调用恢复，不能将一次 MySQL 回滚等同于对象已经删除。

消息流程和内容约束见[消息与附件](../messages/README.md)。

## 线程与调度

| 执行器 | 用途 |
| --- | --- |
| `openchatExecutor` | 默认异步工作与可靠调用执行 |
| `taskScheduler` | 普通业务定时任务 |
| `ipDetailExecutor` | 第三方 IP 信息查询，隔离网络等待 |
| `ssePushExecutor` | SSE 发送，容量有界 |
| `sseHeartbeatExecutor` | SSE 心跳 |
| `liteTopicSubscriptionExecutor` | LiteTopic 订阅重试 |

数据库事务、MQ 确认和线程切换不是同一边界。消费者主体不能脱离确认过程异步执行；异步任务也不能直接使用请求线程的 `RequestHolder`。

## 请求计时与日志

`openchat.plus-web-invoke-time-interceptor` 控制请求计时和响应捕获。`print-header` 会影响请求头输出，部署环境应关闭包含凭据的调试输出。管理路径和 LiveKit 白名单回调从相应调试拦截器排除。

错误页、转发与异步分派有各自的请求生命周期；计时和上下文清理不能仅按首次 Servlet 请求判断。源码由 `FilterConfig`、`RequestContextFilter` 和计时拦截器共同实现。

## 多实例要求

| 共享资源 | 核对事项 |
| --- | --- |
| 数据库 | 唯一索引、乐观锁、状态条件、失败回滚和消息/已读单调性 |
| Redis | 同槽 Lua、主节点读取、持有者校验、持久化及实例间一致配置 |
| MQ | 每节点推送消费组、重投、回查、重试和在途停机 |
| SSE | 消费节点和连接节点匹配；本地连接表只代表本节点 |
| 文件与媒体 | MinIO 和 LiveKit 地址对服务器、客户端均可达 |
| 分布式 ID | 当前消息使用 MyBatis-Plus `IdWorker`；部署核对实际节点标识与时钟条件 |

实现中仍存在需要真实并发和故障注入验证的路径。文档描述机制，不以编译或本地测试作为集群验收证明。

源码入口：[缓存组件](../../openchat-chat-server/src/main/java/com/openchat/common/service/cache/LockedRedisCache.java)、[房间活跃服务](../../openchat-chat-server/src/main/java/com/openchat/chat/service/RoomActivityService.java)、[定时任务](../../openchat-chat-server/src/main/java/com/openchat/common/scheduled/GlobalSchedule.java)、[线程池](../../openchat-chat-server/src/main/java/com/openchat/common/config/ThreadPoolConfig.java)。
