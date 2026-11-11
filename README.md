# OpenChat Server

OpenChat 的 Java 即时通信后端，基于 Spring Boot 构建，提供单聊、群聊、文件助手、消息同步、附件存储与音视频通话服务。

HTTP 接收业务请求，RocketMQ 协调消息处理与通知，SSE 向在线客户端推送事件。音视频通话由 OpenChat 管理权限和呼叫状态，媒体通过 WebRTC P2P 或 LiveKit 传输。

## 主要能力

| 领域 | 功能 |
| --- | --- |
| 用户与好友 | 注册登录、JWT 与设备绑定、个人资料、好友申请、备注及黑名单 |
| 聊天与会话 | 单聊、群聊、文件助手、消息历史、已读未读、置顶、隐藏与免打扰 |
| 消息与附件 | 文本、图片、视频、文件、语音、表情、引用、撤回及 MinIO 分片上传 |
| 音视频 | 单聊与群聊呼叫、P2P 协商、LiveKit 回退、设备归属、通话恢复及回调 |
| 客户端发布 | 管理员认证、安装包发布、版本查询与下载地址查询 |

核心技术：**JDK 25 · Spring Boot 4.0.8 · MyBatis-Plus · MySQL · Redis · RocketMQ V5 · MinIO · LiveKit · Nacos**。

## 项目结构

```text
openchat-server/
  openchat-chat-server/            # 聊天业务、用户、RTC 与管理 API
  openchat-framwork/
    openchat-common-start/        # 公共依赖
    openchat-oss-starter/         # MinIO 集成
    openchat-transaction/         # 可靠调用与 MQ
    openchat-rtc-server/          # 呼叫模型与 LiveKit 集成
  openchat-getway/                 # 独立 API 网关工程
  doc/                            # 项目文档与数据库结构
  pom.xml
```

桌面客户端和管理后台页面独立构建与部署，不包含在本仓库中。API 网关不在根 Maven reactor 内，需要单独构建。

## 文档

所有详细说明统一维护在 [`doc/`](doc/README.md)：

- [项目架构与技术栈](doc/architecture.md)
- [首次部署与启动](doc/deployment/start.md)
- [环境配置](doc/deployment/configuration.md)
- [API 与客户端契约](doc/api.md)
- [RTC 架构、协议与功能说明](doc/rtc/README.md)
- [数据库结构与初始化](doc/sql/README.md)
- [管理 API 与客户端发布](doc/deployment/admin.md)
- [开发与验证](doc/development.md)

首次运行前准备外部服务、环境配置与空数据库，使用 `doc/sql/openchat.sql` 初始化表结构。注册依赖的 `roomId=1` 全员群初始数据需另外准备，具体要求见首次部署说明。
