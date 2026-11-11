# 管理 API 与客户端发布

管理 API 由聊天服务提供，管理页面是单独部署的 `openchat-dashboard` 项目。管理员身份与聊天用户分开，不使用聊天账号 token 访问管理接口。

[文档首页](../README.md) · [配置说明](configuration.md)

## 配置与身份

```properties
openchat.dashboard.enable=true
openchat.dashboard.username=${ADMIN_USERNAME}
openchat.dashboard.password=${ADMIN_PASSWORD}
openchat.dashboard.token-ttl-seconds=3600
```

账号密码来自配置，不读写聊天用户表。JWT 使用 HMAC256，签名密钥由配置账号与密码直接拼接，验证 issuer、audience、subject 和有效期。密码强度决定该签名方案的实际安全性，所有节点使用同一配置。

有效期支持 300 至 86400 秒。开关关闭或配置不完整返回 503，认证失败返回 401。令牌不存 Redis，没有逐 token 撤销接口；关闭功能不会永久撤销尚未到期的令牌。

部署时限制管理入口的网络范围，配置 HTTPS 和入口登录限流。应用不提供网络白名单规则。跨域使用 `openchat.dashboard.allowed-origins` 中的精确 origin；同源反向代理不需要扩大 CORS 范围。

## 接口

| 方法与路径 | 请求 / 返回 |
| --- | --- |
| `POST /system/admin/login` | JSON `{ username, password }`，返回 token、类型和管理员信息 |
| `GET /system/admin/me` | 校验 Bearer token 并返回当前管理员 |
| `GET /system/admin/releases` | 发布记录列表 |
| `POST /system/admin/release/publish` | multipart `clientFile`、`updateInfo` |
| `GET /capi/update/public/latest` | 查询当前发布版本，可无记录返回空数据 |
| `GET /capi/update/public/releases/{releaseId}` | 返回对应安装包的公开下载地址 |

管理接口返回 `ApiResult`。公开更新查询免除聊天 JWT，但仍遵守 `/capi/*` 的平台和设备头校验。

## 安装包发布

支持 `.exe`、`.msi`、`.dmg`、`.pkg`、`.AppImage`、`.deb` 和 `.zip`。服务端从文件名提取 `x.y.z` 版本，如 `openchat-1.0.0-setup.exe`，重新读取内容计算 SHA-256，不信任客户端提交的大小或摘要。

对象保存为：

```text
release/{version}/{sha256}/{uuid}-{clientName}
```

上传成功后向 `client_release` 追加完整记录。版本号唯一，同版本发布失败；安装包及说明需要变更时使用新的版本号。

`getLatest` 按发布记录 ID 倒序取第一条，不按语义版本号比较大小。当前为全局单安装包发布，不提供同版本多平台组合、渠道、草稿、灰度或撤回。

`updateInfo` 可传字符串数组 JSON 或逐行文本，保存为 JSON 数组。历史下载通过不可变 releaseId 定位，不受后续发布影响。

## 存储与失败处理

`oss.bucketName`、`oss.endpoint` 和 `oss.public-endpoint` 需配置齐全，客户端可通过公开地址访问发布对象。

MinIO 和数据库不在同一事务中：

- 上传或确定的同版本唯一冲突失败时尝试清理本次对象。
- 数据库提交结果不确定时保留对象，避免删除可能已经发布的安装包。
- 清理失败记录日志，不能把 HTTP 失败解释为一定没有写入任何外部数据。

发布接口经 Servlet multipart 接收，容器可能使用临时磁盘；其容量和限制需与实际安装包大小匹配。

源码入口：[管理认证](../../openchat-chat-server/src/main/java/com/openchat/system/service/AdminAuthService.java)、[发布服务](../../openchat-chat-server/src/main/java/com/openchat/system/service/ClientReleaseService.java)、[发布 DAO](../../openchat-chat-server/src/main/java/com/openchat/system/dao/ClientReleaseDao.java)。
