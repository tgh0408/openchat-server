# 会话与群聊

[文档首页](../README.md)

## 房间模型

| 类型 | 值 | 消息表 | 权限依据 |
| --- | --- | --- | --- |
| 群聊 | 1 | `message` | 有效群资料与当前群成员 |
| 单聊 | 2 | `message_user` | 真实双人房间参与者；发送另查关系和黑名单 |
| 文件助手 | 3 | `message_user` | `room.owner_uid` 必须等于当前用户 |

文件助手拥有真实正数 roomId，在注册事务中创建房间和本人 contact。`(type, owner_uid)` 唯一约束保证用户归属的唯一性。它复用普通消息、历史、上传和会话能力。

## 会话同步

`POST /capi/chat/contact/list` 返回数组，一次同步当前用户可见的会话，排序为置顶优先、活跃时间倒序、房间 ID 倒序。

列表不以是否仍为好友或群成员作为统一筛选条件。删除好友或离群后，本人会话和本地历史可继续保留。客户端不应因为某次列表未返回一个会话，就删除本地历史。

隐藏或删除的会话在出现超过相应边界的新消息后可以恢复；群聊恢复还要求当前成员资格。房间活跃状态异步合并，会话摘要和排序允许存在短暂延迟。

## 已读与未读

`POST /capi/chat/contact/info/{roomId}` 读取或恢复会话并返回快照，**不会自动标记已读**。用户实际看到消息后，客户端显式确认：

```text
POST /capi/chat/contact/read
```

```json
{
  "roomId": "123",
  "lastReadMsgId": "456"
}
```

服务端检查本人正常会话、房间和消息归属，并单调推进 `contact.last_read_msg_id`。重复确认不会倒退；小于或等于删除边界的确认不会恢复被清除的内容。返回值为最终确认位置。

已读同步是本人状态同步，不授予消息读取权限。未读按可见消息统计，排除本人发送的消息，不用雪花 ID 差值计算。发送消息只推进发送者的已读位置。

对于已无有效成员关系的群，会话未读为 0；会话详情可返回 `groupRemoved=true` 的本人会话快照，不读取群消息正文。单聊未读不因删除好友自动清零。

## 会话操作

| 接口 | 作用 |
| --- | --- |
| `POST /capi/chat/contact/add` | 创建或恢复会话 |
| `POST /capi/chat/contact/remove` | 设置本人清除历史边界 |
| `POST /capi/chat/contact/hide` | 隐藏会话并记录隐藏位置 |
| `POST /capi/chat/contact/notDisturb` | 修改本人的消息免打扰状态 |
| `POST /capi/user/friend/topStatus` | 修改置顶状态 |

`last_delete_id`、`last_hidden_msg_id`、`last_read_msg_id`、置顶与免打扰分别承担不同职责。清除历史不等于全局删除消息；恢复会话也不能随意清零其他偏好。

## 创建与管理群聊

创建请求只接受邀请名单：

```text
POST /capi/group/create
```

```json
{ "uids": ["101", "102"] }
```

原始列表为 1 至 100 人，不能包含本人，服务端去重并验证用户。仅双向有效、未拉黑的好友进入群，至少有一名符合要求的好友才创建成功。群主由当前身份确定，不能从请求指定。

服务端在事务内创建房间、群资料和成员，发布入群及不符合条件的邀请结果提示。返回实际成员数与群资料。

| 接口 | 作用 |
| --- | --- |
| `POST /capi/group/members/add` | 向已有群加入符合条件的好友 |
| `POST /capi/group/members/remove` | 群管理者移出成员 |
| `POST /capi/group/update` | 修改群名称或公告并生成提示 |
| `POST /capi/group/groupInfo/{id}` | 查询群资料 |
| `POST /capi/group/page/group` | 查询本人群列表 |
| `POST /capi/group/page/groupMember/{id}` | 查询群成员 |
| `POST /capi/group/exitGroup/{roomId}` | 退出群聊 |
| `POST /capi/group/delGroup/{id}` | 解散群聊 |

成员角色为 1 群主或超级管理员、2 管理员、3 普通成员。修改群资料、移出、退出和解散由各服务入口校验操作权限，客户端按钮状态不能替代服务端校验。

## 离群与重新加入

离群、移出或解散后，服务端不再向该用户提供群消息正文和附件授权；客户端可以保留此前已经缓存的内容。重新加入后按消息 ID 合并本地与远端数据。

权限变更、消息入库和推送可能并发发生，相关入口都需使用当前群权限判断。已读位置仅记录阅读进度，不授予离群后的远端消息访问权。

源码入口：[ContactService](../../openchat-chat-server/src/main/java/com/openchat/chat/service/ContactService.java)、[ContactReadService](../../openchat-chat-server/src/main/java/com/openchat/chat/service/ContactReadService.java)、[RoomGroupService](../../openchat-chat-server/src/main/java/com/openchat/chat/service/RoomGroupService.java)。
