# 数据库初始化与模型

项目的 MySQL 结构入口为 [openchat.sql](openchat.sql)。脚本包含 23 张表，使用 InnoDB、JSON、毫秒时间和唯一约束，目标数据库需支持其中的 MySQL 8 排序规则与 CHECK 定义。

[文档首页](../README.md) · [首次部署](../deployment/start.md)

## 初始化方式

先创建并选定专用空数据库，再导入 `openchat.sql`。脚本没有 `CREATE DATABASE` 或 `USE`，执行目标由连接方选择；包含 `DROP TABLE IF EXISTS`，不能在有业务数据的库中重复执行。

该文件只有结构，没有用户、群、消息或安装包初始数据。注册服务固定加入 `roomId=1` 的全员群，开放注册前须准备有效的房间、群资料和对应管理关系。仅有空表不足以完成首次注册。

本目录不提供运行期自动建库或自动补数任务；应用运行使用与源码一致的结构和实际业务初始数据。

## 表职责

| 领域 | 表 | 作用 |
| --- | --- | --- |
| 用户 | `user` | 账号、资料、登录凭据字段、客户端内容库密码 |
| 用户 | `user_role` | 用户与角色关系 |
| 好友 | `user_friend` | 本人方向关系、备注、标签、黑名单及版本 |
| 申请 | `user_apply` | 申请消息集合、审批、双方已读状态和私有设置 |
| 房间 | `room` | 房间类型、文件助手归属、活跃时间和最新消息 |
| 房间 | `room_friend` | 单聊双方与唯一房间键 |
| 房间 | `room_group` | 群主、名称、公告等群资料 |
| 群成员 | `group_member` | 用户在群内的角色、昵称及有效状态 |
| 会话 | `contact` | 本人的会话状态、已读、清除/隐藏边界和偏好 |
| 消息 | `message` | 群聊消息 |
| 消息 | `message_user` | 单聊与文件助手消息 |
| 消息 | `message_mark` | 消息标记数据结构 |
| 消息 | `message_resource_ref` | 资源关联表结构；当前附件清理并未据此实现完整共享引用保护 |
| 表情 | `emoji` | 按摘要识别的表情内容 |
| 表情 | `user_emoji` | 用户收藏、删除状态与排序 |
| RTC | `rtc_call` | 呼叫主状态、双方/发起人、版本、设备归属和摘要 |
| RTC | `rtc_group_call_member` | 当前已加入群通话的成员 |
| RTC | `rtc_group_call_member_history` | 每通、每人的参与状态与版本，重入更新同一条 |
| RTC | `rtc_media_session` | P2P/SFU 协商快照及独立版本 |
| RTC | `rtc_webhook_event` | 已验签媒体事件及连接区间观测 |
| 可靠调用 | `secure_invoke_record` | 待执行调用、执行租约和失败记录 |
| 过滤 | `sensitive_word` | 敏感词表 |
| 客户端发布 | `client_release` | 不可变安装包发布记录 |

表或字段存在不等于对应的完整用户功能已经接入，业务范围以服务、Controller 和[功能文档](../architecture.md)为准。

## 关键约束

| 约束 | 业务意义 |
| --- | --- |
| `user` 的邮箱、手机号、openId 唯一索引 | 账号标识唯一 |
| `user_friend(uid, friend_uid)` | 本人方向关系唯一 |
| `room_friend.room_key` | 同一用户对的房间映射唯一 |
| `room(type, owner_uid)` | 文件助手按用户唯一 |
| `contact(room_id, uid)` | 用户在房间中只有一条会话 |
| `group_member(room_id, uid)` | 群成员关系唯一 |
| `user_emoji(uid, emoji_id)` | 用户收藏关系唯一 |
| `rtc_call(caller_uid, client_request_id)` | 呼叫创建请求幂等 |
| `rtc_call.active_key` | 活动通话占用房间，终态释放 |
| `rtc_group_call_member(room_id, uid)` | 当前群参与唯一 |
| `rtc_group_call_member_history(call_id, uid)` | 每通成员状态唯一 |
| `rtc_media_session.call_id` | 一通呼叫一份媒体快照 |
| `rtc_webhook_event.event_id` | 回调事件跨节点去重 |
| `client_release.version_name` | 发布版本唯一 |

`message` 与 `message_user` 的 `client_msg_id` 没有全局唯一索引，因此不能宣称所有普通发送天然按该字段 exactly-once。特定类型的重试与占位完成由其业务实现控制。

## 状态与数据边界

- 群消息与个人消息表不能混查；房间类型是选择存储和权限路径的依据。
- 群通话的 `callee_uid` 为 NULL，成员状态及设备放在群成员表；单聊使用主记录和设备映射。
- RTC 邀请状态、整通版本、成员版本、媒体版本和摄像头版本各有职责，不能混用。
- 可靠调用状态由代码定义为 1 待执行、2 失败、3 执行中，`next_retry_time` 同时承担执行租约边界。
- Webhook 的业务处理可更新通话连接及终态，实际行为以 `RtcWebhookCallService` 为准，不能仅凭表注释推断。
- 清除本人历史使用 contact 边界，不是物理删除全局消息；客户端 SQLite 不属于本 SQL 的管理范围。

## 维护约定

数据结构变化时同步核对实体、Mapper、约束及首次初始化脚本。只读检查、文档整理和代码编译不会自动执行 SQL；真正的数据库操作需要明确的目标及授权。

