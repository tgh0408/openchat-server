# LiveKit 部署与 OpenChat 对接

本文采用 Linux 公网服务器上的 LiveKit、Redis、Caddy 和 Docker Compose 部署方案，使用官方配置生成器准备文件。适用于首次搭建单节点音视频服务；配置与命令依据文末官方资料核对，核对日期为 2026-11-19。

[文档首页](../README.md) · [首次部署](start.md) · [环境配置](configuration.md) · [RTC 协议](../rtc/README.md)

## 接入关系

OpenChat 负责呼叫权限、状态和加入凭据；LiveKit 负责 SFU 音视频传输。以下三条链路都需要可用：

| 方向 | 入口 | 用途 |
| --- | --- | --- |
| 客户端 → LiveKit | `wss://rtc.example.com` 及媒体端口 | 信令连接、加入房间、音视频传输 |
| 聊天服务 → LiveKit | `https://rtc.example.com` | 查询房间、清理已结束通话的房间 |
| LiveKit → OpenChat 网关 → 聊天服务 | `https://api.example.com/capi/livekit/callback` | 已签名的房间和参与者事件 |

域名均为示例，部署时替换成自己的域名。聊天网关现有 `/capi/**` 路由可以转发回调，但没有代理 LiveKit 的信令及媒体端口。`LIVEKIT_URL` 应填写 LiveKit 入口，不能填写 OpenChat 网关的业务地址。

当前代码将同一个 LiveKit URL 下发给客户端，并在访问管理 API 时将 `wss` 转成 `https`。因此这个域名需要同时对客户端和聊天服务器可达，不能只配置一个容器内地址。

## 部署准备

- 准备一台具有公网 IP 的 Linux 服务器和域名解析权限。本文按独立媒体服务器部署，避免与现有网关争用端口。
- 准备 `rtc.example.com` 和 `turn.example.com` 两个域名，均解析到该服务器。使用直接 DNS 解析；普通 HTTP CDN 代理不能代替媒体端口或 TURN 转发。
- 准备可运行 Docker 的配置生成环境。生成配置时不需要在当前 Java 工程中新增依赖。
- 确保 OpenChat 已完成数据库初始化、Nacos 注册、MQ 和 Redis 配置，并能接收 `/capi/livekit/callback`。
- 保持 LiveKit 与聊天服务器时间同步；Token 和 Webhook 签名存在有效期校验。

服务器容量需结合并发人数、视频码率、CPU 和出口带宽测试确定。先验证单节点，录制、外部媒体导入、SIP 和多节点部署不属于本文首次接入范围。

### 网络与端口

下表对应官方 VM 生成方案的常见配置。实际放行范围以生成后的 `livekit.yaml`、`caddy.yaml` 和 Compose 文件为准；云安全组、主机防火墙及 NAT 映射需要一致。

| 端口 / 协议 | 用途 | 访问范围 |
| --- | --- | --- |
| `80/TCP` | Caddy 申请、续期证书 | 公网，按生成配置保留 |
| `443/TCP` | HTTPS / WSS 与 TURN/TLS | 公网，由生成的 Caddy 配置分流 |
| `7881/TCP` | WebRTC ICE/TCP | 公网直达媒体节点 |
| `3478/UDP` | 内置 TURN/UDP，同时提供 STUN | 公网，启用对应配置时 |
| `50000-60000/UDP` | WebRTC ICE/UDP 媒体范围 | 公网直达媒体节点 |
| `7880/TCP` | LiveKit 原始 HTTP / WebSocket API | 仅供 Caddy 或受控内网访问 |
| `6379/TCP` | LiveKit 使用的 Redis | 仅内部访问 |

`443` 可连接不代表媒体可以传输。`7881` 和媒体 UDP 端口不能通过普通 HTTP 路由替代。如果显式配置 `rtc.udp_port` 使用 UDP Mux，则按该单端口放行；此时 `rtc.port_range_start/end` 不再生效，不要混用两种端口方案。

本方案使用生成器提供的 Caddy 配置处理 TLS 和 TURN/TLS，不能仅用普通 `reverse_proxy` 替代整个配置。若自行改成 LiveKit 直接终止 TURN/TLS，则证书、监听端口和负载均衡方案也需要按官方说明调整。

## 生成部署文件

在仓库外创建专用目录，避免把生成的真实 API 密钥提交到 Git。以下为 Linux / macOS shell 命令：

```shell
mkdir -p ~/openchat-livekit-deploy
cd ~/openchat-livekit-deploy
docker pull livekit/generate
docker run --rm -it -v "$PWD:/output" livekit/generate
```

在 PowerShell 中运行容器时，挂载参数可写为：

```powershell
docker run --rm -it -v "${PWD}:/output" livekit/generate
```

按提示填写 LiveKit 和 TURN 域名，首次部署不选 Ingress / Egress，并选择生成启动脚本。生成结果通常位于以域名命名的目录中：

```text
rtc.example.com/
  caddy.yaml
  docker-compose.yaml
  livekit.yaml
  redis.conf
  init_script.sh
```

若选择云初始化方式，生成的是 `cloud_init.*.yaml`；本文后续使用 `init_script.sh`。保管整个生成目录，其中可能包含 API 密钥及嵌入配置的初始化脚本。

## 安装到服务器

先确认两个域名已经解析到目标服务器，且所需端口已放行。将 `init_script.sh` 传到专用 Linux 服务器，检查脚本内容后执行：

```shell
scp rtc.example.com/init_script.sh deploy@media-server:/tmp/livekit-init.sh
ssh deploy@media-server
sudo bash /tmp/livekit-init.sh
```

`deploy@media-server` 替换为实际 SSH 用户和地址。该脚本会安装 Docker / Compose、写入 `/opt/livekit` 配置并建立 `livekit-docker` systemd 服务；不要直接在已运行其他部署的目录上重复初始化。

```shell
sudo systemctl status livekit-docker
cd /opt/livekit
sudo docker-compose ps
sudo docker-compose logs --tail=100
```

本文采用官方脚本使用的 `docker-compose` 命令。若部署环境安装的是 Compose 插件，使用 `docker compose`，并核对 `systemctl cat livekit-docker` 中的实际启动命令。

检查 Caddy 是否成功获取两个域名的证书。证书由生成方案自动管理，不需要复用 OpenChat 网关的证书文件。若 TLS 失败，先检查 DNS、端口占用和公网连通性，再检查日志。

后续修改运行配置以服务器 `/opt/livekit/` 中的文件为准。仅修改生成目录里的同名 YAML，不会自动更新服务器；已经生成的初始化脚本也不一定会重新读取这些文件。

## 配置密钥与回调

在 `/opt/livekit/livekit.yaml` 中保留生成的网络、Redis 和 TURN 配置，核对 `keys` 并补充顶层 `webhook`。下面仅展示需要对齐的片段，不是完整配置：

```yaml
keys:
  REPLACE_WITH_API_KEY: REPLACE_WITH_API_SECRET

webhook:
  api_key: REPLACE_WITH_API_KEY
  urls:
    - https://api.example.com/capi/livekit/callback
```

将两处 `REPLACE_WITH_API_KEY` 替换为生成配置中同一个 API Key，`REPLACE_WITH_API_SECRET` 替换为对应 Secret。已有 `keys` 时直接使用其中的键值，不要追加重复的顶层 `keys` 节点。这些标记仅用于说明，不能原样运行。

本项目 Token 签发、管理 API 和 Webhook 验签共用一组凭据：

| LiveKit 配置 | OpenChat 配置 |
| --- | --- |
| `keys` 中的键名 | `LIVEKIT_API_KEY` |
| 该键对应的值 | `LIVEKIT_API_SECRET` |
| `webhook.api_key` | 必须与上述 `LIVEKIT_API_KEY` 相同 |
| 对外的 HTTPS / WSS 域名 | `LIVEKIT_URL=wss://rtc.example.com` |

API Secret 仅保存在服务端。客户端通过 OpenChat 获取受房间和用户权限约束的短期加入 Token，不持有 API Secret。LiveKit YAML 和 Spring 配置的占位符机制不是一回事，不要假定在 `livekit.yaml` 中写 `${LIVEKIT_API_SECRET}` 就会由容器自动替换。

回调 URL 需要从 LiveKit 服务器访问。如果 OpenChat 网关使用非默认 HTTPS 端口，必须在 URL 中写明，如 `https://api.example.com:99/capi/livekit/callback`。反向代理必须保留 `Authorization`、`Content-Type: application/webhook+json` 和原始请求正文。

修改配置后在维护窗口重启；单节点重启会中断正在进行的媒体连接：

```shell
sudo systemctl restart livekit-docker
sudo systemctl status livekit-docker
```

## 配置 OpenChat

在聊天服务的运行环境中设置下面的变量，替换示例值。其他数据库、MQ、Redis 等变量仍按[环境配置](configuration.md)准备：

```dotenv
LIVEKIT_URL=wss://rtc.example.com
LIVEKIT_API_KEY=REPLACE_WITH_API_KEY
LIVEKIT_API_SECRET=REPLACE_WITH_API_SECRET
STUN_URLS=stun:turn.example.com:3478
```

这是环境变量清单；Spring Boot 不会自动加载 `.env` 文件，需要由部署平台或启动工具导入。上面的 STUN 地址仅在内置 TURN/UDP 的 `3478` 端口已启用并可达时使用。

聊天服务的 `application-prod.properties` 已将这些变量映射到 `rtc.livekit.*`。也可以在外部 Spring 配置中补充：

```properties
rtc.livekit.enabled=true
rtc.livekit.url=${LIVEKIT_URL}
rtc.livekit.api-key=${LIVEKIT_API_KEY}
rtc.livekit.api-secret=${LIVEKIT_API_SECRET}
rtc.livekit.token-ttl-seconds=300

# 首次验收先走 SFU；验证成功后再按需启用单聊 P2P。
rtc.livekit.turns.enable=false
rtc.livekit.stun-urls=${STUN_URLS:}
```

`rtc.livekit.turns.enable` 在本项目中实际映射到 `openchat.rtc.p2p.enabled`，控制单聊 P2P，**不是 LiveKit 的 TURN 开关**。LiveKit TURN 由媒体服务器 `livekit.yaml` 的 `turn` 配置控制。群通话使用 SFU；单聊 P2P 回退后也使用 SFU。

回调路径必须同时包含在以下两组现有白名单中。请合并到已有列表，不要覆盖登录、注册等其他条目：

| OpenChat 属性 | 需包含的精确路径 |
| --- | --- |
| `openchat.auth.white-path` | `/capi/livekit/callback` |
| `openchat.auth.device-white-path` | `/capi/livekit/callback` |

第一组跳过用户 JWT 校验，第二组跳过 `clientId` 和 `X-Device-Id` 校验。回调仍由 LiveKit SDK 验签，不能给它添加普通用户 Bearer Token 来代替 LiveKit 签名。

当前源码仅在 LiveKit 启用且 URL、API Key、Secret 和有效期完整时注册回调 Controller。登录后的 `GET /capi/rtc/config` 返回客户端连接地址；配置未就绪时地址为空。该接口仍要求正常的用户、平台和设备鉴权。

## 接入验收

### 基础连通性

在客户端所在网络以及聊天服务器上检查 LiveKit 的 DNS 和 HTTPS 证书，例如：

```shell
curl -v https://rtc.example.com/
```

根路径的响应只能帮助检查 DNS、TLS 和 HTTP 连通性，不能证明 WebSocket 升级、管理 API 或媒体 UDP 已正常工作。

在 LiveKit 服务器上检查 OpenChat 回调入口：

```shell
curl -i -X POST https://api.example.com/capi/livekit/callback \
  -H 'Content-Type: application/webhook+json' \
  --data '{}'
```

由于没有签名，预期返回 HTTP `401`。这只用于检查入口可达且没有被公开放行；同样的 `401` 也可能由前置鉴权产生，必须继续用真实事件验证。`404` 需检查路由与 Controller 是否注册，`415` 需检查媒体类型。

### 真实通话

1. 启动配置完整的聊天服务和网关，使用两个不同用户、两个实际客户端完成登录。
2. 先保持 P2P 关闭，发起并接听单聊，确认双方取得加入凭据并进入同一 `openchat-call-<callId>` 房间。不要手工伪造房间名或绕过设备归属申请 Token。
3. 检查双方音频和视频订阅、麦克风及摄像头开关，再验证群通话加入和退出。
4. 使用不同网络，例如固定网络与移动网络，检查 UDP 通路；再在受限测试网络中确认 TURN 回退，结合客户端 ICE 统计确认实际采用的连接类型。
5. 检查真实签名回调成功返回 `204`，聊天服务接收到参与者事件，并有对应 `rtc_webhook_event` 数据及通话状态变化。
6. 挂断后确认房间清理、通话摘要与后续 `room_finished` 处理正常；恢复 P2P 后再验证直连及 SFU 回退。

当前项目处理 `room_started`、`participant_joined`、`participant_left` 和 `room_finished`。无关事件、非项目房间或未知呼叫也可能被确认返回 `204`，所以仅看到 HTTP 成功不足以证明业务状态已更新；验收必须使用真实 OpenChat 呼叫。

## 常见问题

| 现象 | 检查方向 |
| --- | --- |
| WSS 连接失败 | DNS、证书信任链、443 监听、Caddy 路由及 WebSocket 升级 |
| 信令连接成功但没有声音或画面 | 媒体 UDP / ICE TCP、防火墙、NAT、公网候选地址、设备权限及订阅状态 |
| 内网能通、公网失败 | DNS 是否仅内网可解析、NAT 映射是否完整、`rtc.use_external_ip` 与实际候选是否一致 |
| 回调一直 401 | 两组白名单、前置鉴权、Key/Secret 是否一致、签名头和原始正文是否被改写、系统时间 |
| 回调 404 或 RTC 配置地址为空 | LiveKit 是否启用、配置是否完整、回调 Controller 是否注册、网关路径 |
| 回调 500 或挂断后房间未清理 | 数据库事务、Redis / MQ，以及聊天服务器访问 LiveKit HTTPS 管理 API 的连通性与凭据 |
| TURN 回退失败 | TURN 域名、证书、生成的 Caddy 分流规则、TURN 监听与公网端口是否对应 |
| 手工测试房间有回调但业务无变化 | 房间是否来自 OpenChat 的真实呼叫，参与者 identity 是否为对应用户 UID |

查看运行日志时使用 `/opt/livekit` 下的 Compose 文件。记录错误类型、房间 ID 和事件 ID 即可，不要将 API Secret、加入 Token 或回调签名头贴入日志和问题单。

## 更新与后续扩容

在生产环境记录并固定验证过的 `livekit/livekit-server` 镜像标签或摘要。这里的服务器镜像版本与 Java 模块中 `io.livekit:livekit-server` SDK 版本不同，不能将 SDK 版本直接当作服务器镜像标签。

更新前保留当前配置和镜像版本，在维护窗口修改 `/opt/livekit/docker-compose.yaml`，拉取并重新创建服务：

```shell
cd /opt/livekit
sudo docker-compose pull
sudo docker-compose up -d
sudo docker-compose ps
```

回滚时恢复原镜像和配置并重新创建容器。升级后重复真实通话和 Webhook 验收；单节点配置不提供无中断升级保障。启用多节点时，还需要统一 Redis、API 密钥、负载均衡、每个媒体节点的公网可达性和节点退出策略，不能只增加 Compose 副本。

## 参考资料

官方资料用于核对部署工具、端口和协议；实际生成结果与所选镜像版本需要一起保存。

| 资料 | 地址 |
| --- | --- |
| 官方 VM 部署与生成器 | `https://docs.livekit.io/transport/self-hosting/vm/` |
| 部署、TLS 与 TURN | `https://docs.livekit.io/transport/self-hosting/deployment/` |
| 端口与防火墙 | `https://docs.livekit.io/transport/self-hosting/ports-firewall/` |
| Webhook 配置与验签 | `https://docs.livekit.io/intro/basics/rooms-participants-tracks/webhooks-events/` |
| 服务端配置样例 | `https://github.com/livekit/livekit/blob/master/config-sample.yaml` |

项目实现参见 [LiveKit 配置](../../openchat-framwork/openchat-rtc-server/src/main/java/com/openchat/rtc/autoconfigure/LiveKitProperties.java)、[回调 Controller](../../openchat-chat-server/src/main/java/com/openchat/webrtc/controller/LiveKitWebhookController.java)、[媒体房间查询与清理](../../openchat-chat-server/src/main/java/com/openchat/webrtc/service/RtcMediaCleanupService.java)。

本文不表示已在目标服务器执行部署；真实媒体、受限网络和回调联调需按上述步骤完成。
