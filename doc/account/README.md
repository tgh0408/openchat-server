# 用户与好友

[文档首页](../README.md) · [通用协议](../api.md)

## 注册与资料

`POST /capi/user/public/register` 使用 `multipart/form-data`，只绑定公开字段：

| 字段 | 约束 |
| --- | --- |
| `userId` | 邮箱或手机号，6 至 20 字符 |
| `username` | 昵称，1 至 16 字符 |
| `password` | 6 至 20 字符 |
| `avatar` | 可选文件，由服务端校验和上传，不接受客户端指定头像 URL |

注册事务保存用户、个人文件助手、本人会话和全员群成员关系。失败时回滚数据库写入；头像对象存储与数据库并非跨系统原子事务。全员群 `roomId=1` 的准备要求见[首次部署](../deployment/start.md)。

用户拥有固定的 `clientSqlPassword`，由登录和 `globalUid` 返回给当前授权账号。本人资料通过 `/capi/user/userinfo` 获取；对方资料通过 `/capi/user/target/info` 查询，按 UID 查询使用 `{ "code": 3, "searchText": "<uid>" }`。

头像响应通过 `MinioBucketService` 转换路径，不在组装响应时修改共享缓存对象。资料显示同时区分公开昵称、群昵称与查看者私有备注。

当前登录和注册流程没有启用验证码校验；获取验证码接口的存在不表示登录已强制校验。

## 登录会话

JWT 包含 uid、设备摘要、平台、创建时间和随机 `jti`，使用 `openchat.auth.secret` 签名。当前签发方法不设置 `exp`；有效会话还必须匹配 Redis 中该用户、该平台保存的 token。

- 同账号的 `PC`、`PHONE`、`WEB` 可以分别在线。
- 同平台显式登录替换该平台 token，其他平台不受影响。
- Redis 会话 TTL 为 3 天，剩余不足 1 天时按条件续期。
- 每次鉴权核对共享会话；本地缓存只用于减少续期判断，不授予登录权限。
- 退出按 token 值原子撤销，迟到退出请求不能删除同平台的新会话。
- 本机事件、MQ 通知和 SSE 心跳共同促使失效连接关闭。

## 好友申请

请求入口为 `/capi/user/friend/apply`。申请人取自登录身份，正文包含目标 UID、申请消息以及本人备注等设置。

| 场景 | 行为 |
| --- | --- |
| 向自己申请 | 拒绝 |
| 双方已有关系 | 拒绝重复建立 |
| 任一方向存在黑名单 | 拒绝申请 |
| 对方仍保留本人，本人方向已删除 | 恢复本人方向，返回 `ADDED`、`targetUid`、`roomId` 等结果 |
| 已有待审批申请 | 在申请消息集合中追加内容并通知，不新建聊天房间 |
| 存在反向有效申请 | 按服务端申请记录完成审批 |
| 需要对方处理 | 保存申请并发送通知，成功数据可能为空 |

待处理申请的双方可以通过 `/replay` 回复。服务端验证参与身份与黑名单，追加消息并将另一方设为未读；回复不直接创建好友关系。

申请列表、未读与阅读入口分别为：

```text
POST /capi/user/friend/apply/page
GET  /capi/user/friend/apply/unread
GET  /capi/user/friend/apply/read/{applyId}
POST /capi/user/friend/agreeApply
POST /capi/user/friend/refuseApply
```

审批使用数据库中真实的申请记录。关系、单聊房间、会话与通知遵守对应事务及唯一约束，不能只依赖“已查询不存在”防止跨节点重复写入。

## 单向关系与黑名单

`user_friend` 按 `(uid, friend_uid)` 保存用户自己的关系与偏好。`room_friend` 保存两名房间参与者，拥有单聊房间不等于拥有所有好友操作权限。

| 操作 | 当前边界 |
| --- | --- |
| 联系人列表与私有资料 | 按本人方向的有效好友关系判断 |
| 删除好友 | 修改本人方向；可请求清除本人的历史边界 |
| 单聊发送 | 接收方必须仍保留发送者，且双方均未拉黑 |
| 单聊历史 | 根据真实房间参与身份及个人删除边界判断，不因单向删除直接销毁消息 |
| 附件下载 | 核对房间归属及账号状态；对方拉黑本人时不签发单聊附件凭证 |
| 群聊 | 好友黑名单不作为群禁言机制，群权限按群成员关系判断 |

主要入口：

```text
POST /capi/user/friend/info
POST /capi/user/friend/remark
POST /capi/user/friend/del/{roomId}
POST /capi/user/friend/black/{roomId}
POST /capi/user/friend/unBlack/{roomId}
```

删除好友、隐藏会话、清除历史和已读位置是不同状态，不能互相替代。

## 备注与排序

联系人按显示名称的拼音排序键和稳定游标分页。名称排序使用用户公开名称或查看者私有备注，中文、多音字处理由现有 `NameSortKey` 完成；客户端不自行推断服务端分页位置。

私有备注、标签等只属于关系持有者，不能写入对方公开资料。群昵称属于群成员记录；消息和会话摘要使用 `DisplayNameService` 按查看者解析显示名称。

源码入口：[UserService](../../openchat-chat-server/src/main/java/com/openchat/user/service/UserService.java)、[LoginService](../../openchat-chat-server/src/main/java/com/openchat/user/service/LoginService.java)、[FriendService](../../openchat-chat-server/src/main/java/com/openchat/user/service/FriendService.java)、[RoomAccessService](../../openchat-chat-server/src/main/java/com/openchat/chat/service/RoomAccessService.java)。
