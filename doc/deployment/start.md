# 首次部署

本文命令在 `openchat-server/` 根目录执行。先准备数据库、外部服务和环境配置，再启动应用。

[文档首页](../README.md) · [配置说明](configuration.md)

## 运行条件

| 组件 | 用途与准备内容 |
| --- | --- |
| JDK 25、Maven | 编译与运行；仓库使用本机 Maven，没有 Maven Wrapper |
| MySQL | 使用支持脚本中 `utf8mb4_0900_*`、JSON 和 CHECK 约束的 MySQL 8 环境 |
| Redis | 登录会话、缓存、Lua 协调与待刷新队列 |
| RocketMQ V5 Proxy | 应用通过 gRPC 入口连接，预先配置 Topic、消费组和权限 |
| Nacos | 聊天服务注册发现；网关还使用配置中心 |
| MinIO | 提前创建配置中的桶，准备对象读写、上传签名和客户端可达地址 |
| LiveKit | 当前默认启用，配置服务端密钥、客户端媒体入口和 Webhook |
| 邮件服务 | 填写应用要求的 `openchat.mail.*` 属性；邮件功能按实际业务使用 |

```shell
java -version
mvn -version
```

## 初始化数据库

结构入口为 [openchat.sql](../sql/openchat.sql)，包含 23 张表。先创建专用空数据库，再在选定数据库中执行脚本。

例如，进入 MySQL 客户端后执行以下命令，用户名、地址及库名使用实际值，密码通过交互输入：

```shell
mysql -h <mysql-host> -P 3306 -u <database-user> -p
```

```sql
CREATE DATABASE openchat CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE openchat;
SOURCE /absolute/path/openchat-server/doc/sql/openchat.sql;
```

Windows 的 `SOURCE` 路径可写为 `F:/openchat/openchat-server/doc/sql/openchat.sql`。脚本含 `DROP TABLE IF EXISTS`，执行目标必须是专用空库；不要在有业务数据的数据库中重复导入。

### 全员群初始数据

`UserService.createUser` 会在注册事务内调用 `RoomGroupService.joinAllUserGroup`，固定加入 `roomId=1` 的全员群。开放注册前，数据库必须具备：

- `room.id=1`，且房间类型为群聊 `type=1`。
- 对应 `room_group.room_id=1` 的有效群资料。
- 与实际运营账号一致的群主及管理关系。

**当前 SQL 只有结构，没有这组初始数据。只导入表结构就尝试注册，会因全员群不存在而回滚。** 初始化数据需按项目实际账号和群资料准备，不能假定注册会自动创建全员群。数据模型及约束见 [SQL 说明](../sql/README.md)。

## 配置中间件

1. 为 MySQL、Redis、Nacos、MinIO、LiveKit 和邮件设置实际连接参数。
2. 创建 `chat_send_msg`、`sse_push`、`rtc_sse_push` 三个事务 Topic。
3. 为每个聊天节点设置稳定且唯一的 `rocketmq.lite.chatnode`，并准备对应 SSE/RTC 消费组。
4. 使用大群 LiteTopic 时，另行准备 Lite 父 Topic、消费组及目标 MQ 的 Lite 能力。
5. 配置 MinIO 客户端可达的上传、签名下载和公开资源入口；配置 LiveKit 媒体网络及回调。

具体属性见[配置说明](configuration.md)，MQ 语义见[消息基础设施](../infrastructure/messaging.md)。

## 构建聊天服务

```shell
mvn -pl openchat-chat-server -am "-DskipTests=true" package
```

当前产物为：

```text
openchat-chat-server/target/openchat-chat-server-1.0-SNAPSHOT.jar
```

项目版本由根 POM 的 `revision` 管理，版本变更后调整产物路径。当前打包方式是包含依赖的 Spring Boot 可执行 JAR。

将实际配置保存在仓库之外，例如 `/etc/openchat/application-prod.properties`：

```shell
java -jar openchat-chat-server/target/openchat-chat-server-1.0-SNAPSHOT.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/openchat/application-prod.properties
```

Windows 可将配置位置替换为 `file:F:/openchat-config/application-prod.properties`。默认 HTTP 端口为 `10086`。

## 构建 API 网关

```shell
mvn -f openchat-getway/pom.xml "-DskipTests=true" package
java -jar openchat-getway/target/openchat-gateway-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:/etc/openchat/gateway.properties
```

网关从 `application.properties` 的 `spring.config.import` 接入 Nacos。外部配置需指定实际 Nacos 地址、命名空间、配置导入和注册信息。监听端口与路由由生效配置决定，本地 YAML 中的注释示例不会自动生效。

## 接入验证

先通过[客户端鉴权协议](../api.md)使用正确的 `clientId`、`X-Device-Id` 完成注册和登录，再验证：

- `globalUid` 返回的 uid 与登录结果一致。
- SSE 可以建立并持续收到心跳、消息和关系事件。
- 文件助手、单聊、群聊能够发送消息，并通过推送或历史查询确认。
- 上传签名在客户端网络可用；附件下载满足当前账号权限。
- 两个实例分别持有 SSE 连接时，普通消息和 RTC 通知均能到达。
- 通话能够接听、挂断，P2P 失败时使用 SFU，LiveKit 回调验签成功。

本页给出部署流程，不代表这些环境验证已经完成。测试入口见[开发与验证](../development.md)。
