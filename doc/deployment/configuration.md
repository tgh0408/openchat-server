# 配置说明

聊天服务的主配置为 `openchat-chat-server/src/main/resources/application.yml`，默认激活 `test`。部署时显式指定 profile 并通过外部文件覆盖环境参数。`test` 只是配置名称；`prod` 文件也没有包含全部中间件属性。

[文档首页](../README.md) · [首次部署](start.md)

## 私密配置填写方式

仓库中的 `test`、`prod` 和网关配置只保留 `${环境变量名}` 占位符及用途注释，不含实际密码、密钥或部署地址。运行前在操作系统、IDE 运行配置或部署平台中设置对应环境变量；没有默认值的占位符必须填写，也可以通过外部 Spring 配置文件覆盖对应属性。不要将真实值改回仓库内的配置文件。

- 聊天服务的环境变量见下方示例；另外设置 `DASHBOARD_USERNAME` 和 `DASHBOARD_PASSWORD` 作为管理后台凭据，`CHAT_JWT_SECRET` 作为 JWT 签名密钥。
- `PC_CLIENT_ID`、`PHONE_CLIENT_ID`、`WEB_CLIENT_ID` 必须与相应客户端约定一致。
- 网关使用 `NACOS_ADDRESS`、`NACOS_USER`、`NACOS_PASSWORD`、`NACOS_CONFIG_NAMESPACE` 和 `GATEWAY_DISCOVERY_IP`；配置命名空间与聊天服务的发现命名空间 `NACOS_NAMESPACE` 是不同用途。
- Maven 中的 `docker.host` 通过 `DOCKER_HOST` 提供，使用 Docker 部署时再配置。

本地也可以创建已被 Git 忽略的 `application-secrets.properties`，通过 `--spring.config.additional-location=file:./application-secrets.properties` 显式加载，并在该文件中填写实际属性值。聊天服务和网关使用各自的私密配置文件。`.env` 文件同样被忽略，但 Spring Boot 不会自动加载它，需要由启动工具导入环境变量。

## 环境属性

主 YAML 使用以下属性作为占位符来源：

| 配置 | 用途 |
| --- | --- |
| `openchat.mysql.ip/port/db/username/password` | MySQL 地址、库名及凭据 |
| `openchat.redis.host/port/password` | Redis 连接 |
| `nacos.discovery.server-addr/username/password/namespace` | 聊天服务注册发现 |
| `rocketmq.lite.endpoints` | RocketMQ V5 Proxy gRPC 入口，普通 MQ 和 Lite 客户端共用 |
| `rocketmq.access-key/secret-key` | MQ 访问凭据 |
| `rocketmq.lite.ssl` | MQ TLS 开关 |
| `rocketmq.lite.chatnode` | 节点标识，决定节点级推送消费组 |
| `rocketmq.lite.enabled` | LiteTopic 功能开关 |
| `oss.enabled/type/endpoint/access-key/secret-key/bucketName` | MinIO 开关、类型、地址、凭据和桶名 |
| `oss.upload-endpoint` | 客户端上传及私有附件下载签名入口；未配置时沿用 endpoint |
| `oss.public-endpoint` | 公开资源与消息稳定地址的前缀 |
| `oss.region` | 可选存储区域，必须与实际 MinIO 区域一致 |
| `rtc.livekit.url/api-key/api-secret/token-ttl-seconds` | LiveKit 服务、密钥和 Token 有效期 |
| `rtc.livekit.turns.enable` | 当前映射到 `openchat.rtc.p2p.enabled` |
| `rtc.livekit.stun-urls` | 单聊直连使用的 STUN 地址列表 |
| `openchat.mail.host/protocol/auth/user/password` | 邮件属性 |

`rtc.livekit.turns.enable` 在本项目中的作用是 P2P 开关，不能据其名称认定应用已经配置 TURN。媒体服务器的端口、证书、STUN/TURN 和外部可达性在 LiveKit 部署中另行设置。

## 外部配置示例

下列文件内容使用环境变量占位，填写实际部署环境后才能启动。各平台 `clientId` 需要与实际客户端约定一致。

```properties
openchat.mysql.ip=${DB_HOST}
openchat.mysql.port=3306
openchat.mysql.db=openchat
openchat.mysql.username=${DB_USER}
openchat.mysql.password=${DB_PASSWORD}
openchat.redis.host=${REDIS_HOST}
openchat.redis.port=6379
openchat.redis.password=${REDIS_PASSWORD}

nacos.discovery.server-addr=${NACOS_ADDRESS}
nacos.discovery.username=${NACOS_USER}
nacos.discovery.password=${NACOS_PASSWORD}
nacos.discovery.namespace=${NACOS_NAMESPACE}

rocketmq.lite.endpoints=${MQ_PROXY_ENDPOINTS}
rocketmq.access-key=${MQ_ACCESS_KEY}
rocketmq.secret-key=${MQ_SECRET_KEY}
rocketmq.lite.ssl=false
rocketmq.lite.enabled=false
rocketmq.lite.chatnode=chat-node-1

oss.enabled=true
oss.type=minio
oss.endpoint=${MINIO_INTERNAL_ENDPOINT}
oss.upload-endpoint=${MINIO_CLIENT_ENDPOINT}
oss.public-endpoint=${MINIO_PUBLIC_ENDPOINT}
oss.access-key=${MINIO_ACCESS_KEY}
oss.secret-key=${MINIO_SECRET_KEY}
oss.bucketName=${MINIO_BUCKET}

rtc.livekit.url=${LIVEKIT_URL}
rtc.livekit.api-key=${LIVEKIT_API_KEY}
rtc.livekit.api-secret=${LIVEKIT_API_SECRET}
rtc.livekit.token-ttl-seconds=300
rtc.livekit.turns.enable=true
rtc.livekit.stun-urls=${STUN_URLS:}

openchat.auth.secret=${CHAT_JWT_SECRET}
openchat.auth.client-id.PC=${PC_CLIENT_ID}
openchat.auth.client-id.PHONE=${PHONE_CLIENT_ID}
openchat.auth.client-id.WEB=${WEB_CLIENT_ID}
openchat.dashboard.enable=false
openchat.plus-web-invoke-time-interceptor.enable=false
openchat.plus-web-invoke-time-interceptor.print-header=false

openchat.mail.host=${MAIL_HOST}
openchat.mail.protocol=${MAIL_PROTOCOL}
openchat.mail.auth=true
openchat.mail.user=${MAIL_USER}
openchat.mail.password=${MAIL_PASSWORD}
```

每个实例的 `rocketmq.lite.chatnode` 必须不同；共享的 JWT、媒体密钥、数据库、Redis 和桶配置保持一致。启用管理 API 时补齐[管理员配置](admin.md)。

## 业务参数

| 参数 | 当前默认值 / 主配置值 | 含义 |
| --- | --- | --- |
| `server.port` | 10086 | 聊天 HTTP 端口 |
| `sse.enabled` | true | 资源 SSE |
| `sse.path` | `/resource/sse` | 与控制器前缀组成 `/capi/resource/sse` |
| `sse.heartbeat-interval-seconds` | 15 | SSE 心跳间隔 |
| `sse.push-core-threads/max-threads/queue-capacity` | 4 / 16 / 1000 | SSE 推送执行器 |
| `openchat.chat.room-activity.window-ms` | 1000 | 活跃状态合并窗口 |
| `openchat.chat.room-activity.poll-ms` | 500 | 刷新任务 fixedDelay |
| `openchat.chat.room-activity.batch-size` | 500 | 每轮房间批量上限 |
| `openchat.chat.push.large-group-member-threshold` | 200 | 大群提升阈值 |
| `rocketmq.producer.request-timeout` | 3 秒 | MQ 请求超时 |
| `rocketmq.producer.max-attempts` | 3 | 包含首次发送的尝试次数 |
| `sensitive-word.enabled` | false | 敏感词过滤开关 |
| `spring.servlet.multipart.max-file-size/max-request-size` | 2GB / 2GB | 经过应用的 multipart 限制，不是 MinIO 直传上限 |

默认值应结合 YAML、属性类及 `@Value` 一起判断。例如房间轮询的默认值来自 `GlobalSchedule`。

## 网关与反向代理

- 网关通过 Nacos 配置中心加载路由，并通过服务发现访问聊天实例。
- SSE 需要持续转发流式响应，并设置适合长连接的代理超时和缓冲策略。
- MinIO 的签名 Host、路径和查询参数必须保持一致。`MinioHostHeaderFilter` 只处理路由 ID 为 `minio` 的请求。
- LiveKit 回调精确路径为 `/capi/livekit/callback`，保留原始 UTF-8 正文及 Authorization。
- 浏览器直连需要匹配实际 origin 的 CORS 配置，不能通过放宽鉴权解决跨域问题。

配置文件中的实际密码和访问密钥属于部署环境，不写入公开文档或日志。是否关闭某组件也取决于业务 Bean 依赖，不能只关闭一个开关就假定完整聊天服务可脱离该组件运行。
