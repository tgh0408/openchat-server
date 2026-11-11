# 开发与验证

[文档首页](README.md)

## 工作区与构建

后端根目录是独立 Git 仓库。根 POM 聚合聊天和框架模块，API 网关单独构建。修改前检查工作区状态并阅读目标模块与调用方，不覆盖其他正在进行的修改。

```shell
mvn -pl openchat-chat-server -am "-DskipTests=true" compile
mvn -f openchat-getway/pom.xml "-DskipTests=true" compile
```

JDK 使用 25，文件编码 UTF-8。父 POM 默认 `skipTests=true`，执行测试时显式覆盖。修改依赖或自动配置后，核对整个调用链，不只单独编译被编辑文件。

## 业务实现约定

- Controller 处理入参、当前身份与响应，业务规则放 Service，持久化放 DAO/Mapper。
- 请求和响应使用 VO，内部传递使用 DTO，避免直接把持久化实体扩展成外部协议。
- 复用现有 `ApiResult`、业务异常、权限服务、adapter、消息处理器与共享缓存入口。
- 多个离散状态分支优先使用 switch，不新增嵌套三元表达式。
- 不为简单业务添加不必要的重复查询、通用恢复框架或自动循环重试。
- 新增写入检查结果，普通冲突抛出业务异常并回滚；明确的幂等链路按原契约处理。

## 事务、锁与集群

项目按多实例运行设计。JVM 锁、本地 Map 和进程内事件不能作为跨节点唯一性或投递保障。

实体已使用 `@Version` 时优先采用插件支持的更新方法，保留读取到的版本；Wrapper 或自定义 SQL 不一定自动获得乐观锁保障。

全局禁止新增显式悲观锁。已有 Redis 锁调用需核对持有者、租期、释放和迟到写入条件，不能简单根据“曾经取得锁”判断可以继续写入。

有缓存的读取使用现有入口；事务内是否回源由缓存组件负责。未经明确同意不新增或改写 Mapper XML SQL。

数据库、Redis、MQ、OSS 和 LiveKit 不是一个本地事务。每条跨系统流程都应明确已完成的状态、失败后的查询与重试入口。

## 协议修改

HTTP、MQ、SSE、数据库和客户端类型是同一协议链的不同环节。修改字段、枚举或错误语义时同时检查：

1. 请求/响应 VO 和全局 Jackson 配置。
2. 消息生产、消费与推送 DTO。
3. 当前表结构及状态机。
4. 对应客户端的类型与运行时校验。

标识保持字符串，消息发送成功不返回消息实体，客户端通过事件或历史确认。详情读取和显式已读确认保持独立。

## 定向测试

以下命令引用仓库中的测试类，执行后应记录实际结果与环境。

MQ 生命周期与 LiteTopic：

```shell
mvn -pl openchat-framwork/openchat-transaction -am "-DskipTests=false" "-Dtest=MqConsumerShutdownTest,LiteTopicClientShutdownTest,LiteTopicClientTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

好友申请回复与消息类型：

```shell
mvn -pl openchat-chat-server -am "-DskipTests=false" "-Dtest=FriendApplicationReplyTest,TemporaryMessageTypeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RTC 群成员及回调：

```shell
mvn -pl openchat-chat-server -am "-DskipTests=false" "-Dtest=RtcGroupJoinTest,RtcGroupDepartureTest,RtcWebhookServiceTest,RtcWebhookTransactionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

运行其他测试前先阅读测试类的连接和启动方式，不能从 `test` profile 名称判断环境是隔离的。文档改动只需核对引用、协议和源码；不能为了检查文档自动启动真实服务或导入数据库。

## 首次发布验收

| 领域 | 需要验证的行为 |
| --- | --- |
| 初始化 | 空库结构、全员群初始数据、注册原子性、文件助手归属 |
| 认证 | 同平台替换、跨平台并存、设备不匹配、迟到退出、SSE 失效 |
| 消息 | 重复发送、MQ 回查、乱序推送、重连补洞、显式已读单调性 |
| 上传 | 分片恢复、取消与完成竞争、撤回清理、权限变化和对象不可用 |
| 群聊 | 并发加人/移出、离群后拒绝远端读取、群提示与缓存失效 |
| RTC | 多端抢接、群加入/退出、P2P/SFU、回调重复乱序、重连与挂断竞争 |
| 集群 | 两个实例的 SSE 路由、共享缓存、任务租约、节点终止与正常停机 |
| 发布 | 同版本并发、安装包摘要、存储与数据库部分成功 |

区分静态源码检查、单元测试、Mock 回归和真实多实例联调，记录实际执行结果及尚未覆盖的场景。
