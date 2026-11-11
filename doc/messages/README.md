# 消息与附件

[文档首页](../README.md) · [MQ 与送达](../infrastructure/messaging.md)

## 消息类型

| `msgType` | 类型 | 用途 |
| --- | --- | --- |
| 1 | TEXT | 文本消息 |
| 2 | RECALL | 原位撤回消息 |
| 3 | TEMP | 视频或文件上传占位 |
| 4 | IMG | 普通图片 |
| 5 | VIDEO | 视频与封面 |
| 6 | FILE | 普通文件 |
| 7 | VOICE | 音频消息，使用独立处理器 |
| 8 | SOUND | 录音语音条 |
| 9 | EMOJI | 图片表情 |
| 10 | SYSTEM | 业务生成的系统提示 |
| 11 | RTC_CALL | 服务端生成的通话摘要 |

普通客户端不能通过发送入口伪造 SYSTEM 或 RTC_CALL。语音条使用 type 8；type 7 的存在不表示它与录音语音条具备相同内容校验和客户端支持。

## 普通发送

```text
POST /capi/chat/msg
```

```json
{
  "roomId": "123",
  "msgType": 1,
  "clientMsgId": "client-generated-message-id",
  "body": { "content": "你好" }
}
```

`roomId` 和 `clientMsgId` 必填。消息 ID 由服务端生成，`clientMsgId` 用于客户端关联发送确认，不能替代服务端 ID 或所有类型的数据库去重约束。

提交 MQ 前和本地事务落库时都校验房间、身份及内容。成功 HTTP 响应 `data` 为空；客户端通过后续 SSE 或历史查询确认消息实体。

群聊存入 `message`，单聊与文件助手各存一份 `message_user`。处理器在一次写入中填齐正文和扩展信息。

## 引用

引用使用请求顶层 `replyId`：

```json
{
  "roomId": "123",
  "msgType": 1,
  "clientMsgId": "reply-client-id",
  "replyId": "456",
  "body": { "content": "收到" }
}
```

`replyId` 是原服务端消息 ID，并且须早于新消息。可引用文本、图片、视频、文件、音频、语音和表情；已知的撤回、系统、占位等类型不能作为引用目标。原消息不可访问时允许发送正文，引用展示按可用状态处理，不泄露不可读取的正文。

`gapCount` 是数据库快照中的间隔消息条数，不是 ID 差值，也不保证能够代替分页游标。引用本身不等于跨会话转发；当前没有独立的消息转发接口或完整共享附件引用清理流程。

## 图片与语音直传

先调用 `POST /capi/oss/upload/url` 获取签名，客户端上传成功后再发送消息。正文保存对象路径或稳定地址，不保存临时 PUT 签名作为消息内容。

| 内容 | 服务端请求约束 |
| --- | --- |
| 普通图片 | 最大 50 MiB；上传地址与内容路径对应同一对象 |
| 语音条 | WAV，16 kHz、单声道、16-bit PCM；1 至 120 秒 |
| 语音大小 | 32044 至 3840044 字节，服务端同时核对对象大小和 44 字节格式头 |
| 视频 | 最大 2 GiB；时长为毫秒，1 至 86400000 |
| 视频封面 | JPEG，最大 2 MiB；宽高各 1 至 1920 |
| 普通文件 | 最大 6 GiB |

语音条不创建上传占位。图片、视频、文件和语音各由对应处理器检查实际对象归属、大小及格式，上传成功本身不能替代消息权限检查。

## 文件与视频分片

文件和视频使用上传会话与 type 3 占位：

1. `POST /capi/chat/msg/temporary` 创建或获取消息占位；`targetType` 为 5 或 6。
2. `POST /capi/oss/multipart/initiate` 创建或获取上传会话，校验已存在的占位并绑定账号、房间和 `clientMsgId`。
3. `POST /capi/oss/multipart/part-url` 获取分片 PUT 签名，客户端上传各分片。
4. `POST /capi/oss/multipart/complete` 校验并合并对象。
5. `PUT /capi/chat/msg/temporary` 在同一消息 ID 上完成类型和正文替换，视频使用 `body`，文件使用 `fileBody`。
6. 通过推送或 `/msg/temporary/state` 确认最终状态；取消消息使用 `/msg/temporary/cancel`，中止上传使用 `/capi/oss/multipart/abort`。

上传会话保存在 Redis，保留期为 7 天。完成操作会核对占位身份、元数据及绑定对象，不能让另一账号或迟到上传覆盖已经取消、完成或撤回的消息。

MinIO 上传与消息数据库提交存在分步完成状态。客户端失败后需查询状态再决定继续，不因本地超时直接假定服务端失败。

## 历史、补查与下载

- `/capi/chat/msg/page` 按游标分页，使用房间快照上界及本人历史删除边界。
- `/capi/chat/msg/state` 查询缓存消息的当前状态，不标记已读。
- `/capi/chat/msg/temporary/state` 补查上传占位终态。
- `/capi/chat/msg/download/url` 依据真实 roomId、msgId 校验当前权限并生成临时下载签名。

签名下载入口使用 `oss.upload-endpoint`，未配置时沿用 `oss.endpoint`。`oss.public-endpoint` 用于公开资源和稳定地址前缀，不能用它替换已经签名的 URL。

## 撤回与资源清理

撤回也走普通发送入口，`msgType=2`，`body` 为 `{ "msgId": "<目标消息ID>" }`。它在原消息 ID 上写入撤回状态和操作者信息，不生成一条独立普通消息来覆盖排序。

本人可在 2 分钟内撤回文本、图片、视频、文件、表情和语音条。群管理者按权限可以撤回成员消息及上传占位；本人未完成的上传使用取消入口。撤回不附带引用目标。

消息更新、可靠通知和资源清理任务在事务内登记。对象删除在提交后执行，失败由可靠调用重试；已经确认的撤回不因 OSS 清理失败而回滚。

## 表情收藏

`GET /capi/user/emoji/list` 获取收藏；`POST /capi/user/emoji` 新增或收藏，`/delete` 删除，`/front` 移到最前。文件接收与 JSON 请求使用 Controller 中对应的表单定义。

内容存于 `emoji`，用户收藏关系存于 `user_emoji`。排序值越小越靠前，收藏恢复和置前仅更新相关状态，不能重置其他用户的排序。

源码入口：[消息请求](../../openchat-chat-server/src/main/java/com/openchat/chat/domain/vo/req/ChatMessageReq.java)、[处理器基类](../../openchat-chat-server/src/main/java/com/openchat/chat/service/abstracts/AbstractMsgHandler.java)、[占位服务](../../openchat-chat-server/src/main/java/com/openchat/chat/service/TemporaryMessageService.java)、[撤回处理器](../../openchat-chat-server/src/main/java/com/openchat/chat/service/abstracts/RecallMsgHandler.java)。
