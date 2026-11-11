# API 与客户端契约

普通业务使用 HTTP，实时事件通过资源 SSE 推送。服务默认基址为 `http://localhost:10086`，客户端设置基址时不附加完整接口路径。

[文档首页](README.md)

## 身份校验

普通 `/capi/*` 请求需要：

```http
clientId: <与服务端平台配置匹配的标识>
X-Device-Id: <64 位小写十六进制设备摘要>
Authorization: Bearer <登录返回的 token>
```

`clientId` 对应 `PC`、`PHONE` 或 `WEB` 平台，不是用户身份。`X-Device-Id` 必须匹配 `[a-f0-9]{64}`，JWT 校验绑定平台和设备。

公开注册、登录和更新查询免除用户 JWT，但仍经过平台、设备过滤。管理 API 使用独立管理员 JWT；LiveKit 回调使用专用签名校验，不能使用聊天 JWT 代替。

用户身份从服务端请求上下文获取。异步工作和 MQ 消费显式传递业务身份，不能依赖 HTTP 线程上下文仍然存在。

## 响应与序列化

普通响应为 `ApiResult<T>`：

```json
{
  "success": true,
  "code": 200,
  "errCode": null,
  "errMsg": null,
  "data": null
}
```

客户端同时处理 HTTP 状态和 `success`。分页使用响应中的 `cursor`、`isFirst`、`isLast` 和 `list`，不自行计算下页游标。

Java `Long` 序列化为字符串，包括用户、房间、消息标识及 Long 类型计数。不要使用 JavaScript `Number` 运算雪花 ID。

全局 Jackson 启用了枚举序号输出，但带 `@JsonValue` 的枚举使用声明的值。消息类型、房间类型和 RTC 字段应分别按文档解析，不能假定所有枚举都是字符串或都从零开始。

## 登录流程

1. `POST /capi/user/public/login` 提交 `{ "userId": "...", "password": "..." }`。
2. 成功读取 `data.uid`、`data.token` 和 `data.clientSqlPassword`。
3. 使用相同平台、设备和 Bearer token 请求 `GET /capi/user/globalUid`。
4. 该接口的 `data` 是 `{ "uid": "...", "clientSqlPassword": "..." }`，不是裸 uid。核对身份后再开放该账号的数据访问。
5. 获取本人资料、同步会话并建立 SSE。

认证失效时停止使用该 token。网络错误与认证失败应区别处理，不能把失败请求伪装为离线授权成功。

## 主要业务入口

| 方法与路径 | 用途 |
| --- | --- |
| `POST /capi/user/public/register` | multipart 注册，字段见[账号说明](account/README.md) |
| `GET /capi/user/public/getCaptcha` | 获取验证码 |
| `POST /capi/user/loginOut` | 退出当前平台会话 |
| `GET /capi/user/userinfo` | 本人资料 |
| `POST /capi/user/target/info` | 按账号或 UID 查询资料 |
| `POST /capi/user/changeUserInfo` | 修改资料 |
| `POST /capi/user/changePassword` | 修改密码 |
| `POST /capi/user/friend/apply` | 发起好友申请 |
| `POST /capi/user/friend/replay` | 回复待处理申请，保留路径拼写 |
| `POST /capi/user/friend/agreeApply` | 同意申请 |
| `POST /capi/user/friend/refuseApply` | 拒绝申请 |
| `POST /capi/user/friend/page` | 游标查询联系人 |
| `POST /capi/chat/contact/list` | 同步全部可见会话，`data` 为数组 |
| `POST /capi/chat/contact/info/{roomId}` | 会话详情快照 |
| `POST /capi/chat/contact/read` | 确认实际已读消息 |
| `POST /capi/chat/msg` | 发送或撤回消息，成功 `data` 为空 |
| `POST /capi/chat/msg/page` | 分页历史 |
| `POST /capi/chat/msg/state` | 消息状态补查 |
| `POST /capi/chat/msg/download/url` | 附件临时下载授权 |
| `POST /capi/group/create` | 创建群聊 |
| `POST /capi/oss/upload/url` | 请求直传签名 |
| `GET /capi/user/emoji/list` | 表情收藏列表 |

会话与群组操作详见[会话文档](chat/README.md)，分片和上传占位详见[消息文档](messages/README.md)，RTC 接口详见[通话文档](rtc/README.md)，管理入口详见[发布管理](deployment/admin.md)。

## SSE

```text
GET /capi/resource/sse?connectionType=LOGIN
GET /capi/resource/sse?connectionType=RECONNECT
GET /capi/resource/sse/close
```

`connectionType` 缺省为 `RECONNECT`。首次登录连接使用 `LOGIN`，可触发登录 IP 等附加处理；网络重连使用 `RECONNECT`。响应类型为 `text/event-stream`，按 SSE 字段解析。

客户端收到事件后按事件类型处理消息、关系或 RTC 状态。消息发送使用 `clientMsgId` 关联推送确认，通话控制通过服务端快照核对最终状态。断线重连后补查历史或状态，不能把 SSE 当作可靠离线消息库。

SSE 心跳会复核登录会话。主动退出、同平台新登录以及 token 失效后的连接不能继续使用原阅读权限。

## 协议依据

字段以请求/响应 VO 和 Controller 为准，运行时可查看 Springdoc 生成的 OpenAPI。关键源码：

- [请求上下文过滤器](../openchat-chat-server/src/main/java/com/openchat/common/filter/RequestContextFilter.java)
- [全局序列化](../openchat-chat-server/src/main/java/com/openchat/common/config/JacksonConfig.java)
- [用户 Controller](../openchat-chat-server/src/main/java/com/openchat/user/controller/UserController.java)
- [SSE Controller](../openchat-chat-server/src/main/java/com/openchat/common/sse/controller/SseController.java)
