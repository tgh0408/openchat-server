# OpenChat API 网关

网关负责统一接入聊天 HTTP API、SSE 和 MinIO 对象存储请求，通过 Nacos 加载路由并发现聊天服务。聊天鉴权与业务处理由下游服务完成，LiveKit 媒体连接使用独立入口。

[项目首页](../../README.md) · [服务端部署](../../doc/deployment/start.md) · [环境配置](../../doc/deployment/configuration.md) · [网关配置样例](openchat.yaml)

## 工程与运行环境

| 项目 | 当前配置 |
| --- | --- |
| 工程目录 | `openchat-getway/`，目录名保留现有拼写 |
| Maven artifactId / 应用名 | `openchat-gateway` |
| 启动类 | `com.openchat.OpenChatGateWayApplication` |
| Java | JDK 25 |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| 网关实现 | Spring Cloud Gateway Server WebFlux |

网关是独立 Maven 工程，不在根项目的 reactor 中，需要单独构建。以下命令均在仓库根目录 `openchat-server/` 执行。

```text
openchat-getway/
  doc/
    README.md                       # 本文
    openchat.yaml                    # 发布到 Nacos 的配置样例
  src/main/
    java/com/openchat/
      config/                       # CORS、缓存、未匹配路由处理
      filter/                       # 客户端 IP 与 MinIO Host 转发
      OpenChatGateWayApplication.java
    resources/
      application.properties        # Nacos 连接与配置导入
      application.yml               # 注释示例，不提供生效路由
  src/test/                         # MinIO 转发回归测试
  pom.xml
```

## 配置加载

[`application.properties`](../src/main/resources/application.properties) 中的导入配置为：

```properties
spring.config.import=nacos:openchat-gateway.yml?group=DEFAULT_GROUP&refreshEnabled=true
```

`doc/openchat.yaml` 是部署参考文件，不在应用资源目录中，也不会被启动过程自动读取。发布到 Nacos 时使用下面的配置标识，不能直接把本地文件名当作 Data ID。

| 配置项 | 填写内容 |
| --- | --- |
| Namespace | `NACOS_CONFIG_NAMESPACE` 指定的命名空间 ID |
| Group | `DEFAULT_GROUP` |
| Data ID | `openchat-gateway.yml` |
| 配置格式 | YAML |
| 配置内容 | 按部署环境调整后的 `openchat.yaml` 内容 |

### 本地连接参数

启动前在操作系统、IDE 运行配置或部署平台中设置以下环境变量：

| 环境变量 | 用途 |
| --- | --- |
| `NACOS_ADDRESS` | Nacos 地址，格式为 `host:port` |
| `NACOS_USER` | Nacos 用户名 |
| `NACOS_PASSWORD` | Nacos 密码 |
| `NACOS_CONFIG_NAMESPACE` | 存放网关 YAML 的配置命名空间 ID |
| `GATEWAY_DISCOVERY_IP` | 注册到 Nacos 的网关 IP，需能被相关服务访问 |

配置命名空间决定“从哪里读取 YAML”；`spring.cloud.nacos.discovery.namespace` 决定“在哪个命名空间发现服务”。两者可以不同，但网关的服务发现命名空间必须与聊天服务保持一致，才能通过 `lb://openchat-server` 找到实例。

也可以在仓库外保存 Spring 属性文件，用启动参数 `--spring.config.additional-location=file:/absolute/path/gateway.properties` 加载。真实密码和证书私钥不要写入仓库；`.env` 需要由启动工具导入，应用不会自动读取。

### 调整 Nacos YAML

当前 [`openchat.yaml`](openchat.yaml) 包含具体环境的地址、命名空间及证书路径，使用前逐项替换：

| YAML 属性 | 调整要求 |
| --- | --- |
| `server.port` | 网关监听端口；样例为 `99` |
| `server.ssl.enabled` | 样例启用 HTTPS；由前置代理终止 TLS 时按部署方式调整 |
| `server.ssl.certificate` | 当前主机或容器内可读取的证书路径 |
| `server.ssl.certificate-private-key` | 与证书匹配的私钥路径 |
| `spring.cloud.nacos.discovery.namespace` | 聊天服务所在的服务发现命名空间 ID |
| `spring.cloud.gateway.server.webflux.routes` | 业务服务和 MinIO 的路由规则 |

发布配置时可以使用环境变量占位符，以下变量需要在网关进程环境中提供：

```yaml
server:
  port: 99
  ssl:
    enabled: true
    certificate: ${TLS_CERTIFICATE_PATH}
    certificate-private-key: ${TLS_PRIVATE_KEY_PATH}

spring:
  cloud:
    nacos:
      discovery:
        namespace: ${NACOS_NAMESPACE}
    gateway:
      server:
        webflux:
          routes:
            - id: openchat-server
              uri: lb://openchat-server
              predicates:
                - Path=/capi/**
            - id: minio
              uri: ${MINIO_INTERNAL_ENDPOINT}
              predicates:
                - Path=/openchat/**
              filters:
                - PreserveHostHeader
```

`MINIO_INTERNAL_ENDPOINT` 填写网关可访问的 MinIO S3 API 地址，包含协议和端口。`/openchat/**` 对应名为 `openchat` 的桶；桶名变化时同步调整路径规则。

## 路由与转发

| 路由 ID | 匹配路径 | 目标与用途 |
| --- | --- | --- |
| `openchat-server` | `/capi/**` | `lb://openchat-server`，通过 Nacos 发现聊天实例 |
| `minio` | `/openchat/**` | MinIO S3 API，转发对象上传、下载及桶查询请求 |

当前配置没有去除路径前缀，下游收到原始请求路径。

- SSE 默认路径 `/capi/resource/sse` 由聊天路由转发。前置反向代理需要支持持续的流式响应，并配置合适的超时和缓冲策略。
- LiveKit Webhook `/capi/livekit/callback` 同样经过聊天路由；原始请求体和 `Authorization` 用于下游验签。
- `/system/admin/**` 不在现有路由范围内。管理 API 如需通过网关访问，需另行配置路由与访问范围。
- LiveKit 的 WebSocket 信令及媒体端口没有在此配置中代理，需要按 LiveKit 部署方案独立提供。

### MinIO 签名

[`MinioHostHeaderFilter`](../src/main/java/com/openchat/filter/MinioHostHeaderFilter.java) 仅对 ID 为 `minio` 的路由保留原始 Host，包括非默认端口。YAML 中的 `PreserveHostHeader` 也表达相同要求。

客户端访问入口需与聊天服务生成预签名 URL 使用的入口一致。转发链路应保持签名涉及的 Host、路径、查询参数及请求体，不要将 Host 改成 MinIO 内网地址，也不要额外重写对象路径。桶区域查询 `/openchat?location=` 也在转发范围内。

### 其他行为

| 实现 | 当前行为 |
| --- | --- |
| `RealIPFilter` | 优先取 `X-Forwarded-For`，缺失时取连接来源地址，再写入 `X-Real-IP`；前置代理需管理可信转发头 |
| `CorsConfig` | 当前允许任意 origin pattern、请求头和方法，并允许凭据；面向限定站点部署时需在该实现中收紧来源 |
| `BlankNotFoundHandlerMapping` | 未匹配到路由时返回空响应体的 404，并记录日志；不改写下游自己返回的 404 |
| `CacheConfig` | 注册 Caffeine 缓存管理器，不代表已配置 API 响应缓存 |

## 构建与启动

先准备 Nacos 配置、环境变量、证书，以及已注册的聊天服务和可访问的 MinIO。

```shell
java -version
mvn -version
mvn -f openchat-getway/pom.xml "-DskipTests=true" package
java -jar openchat-getway/target/openchat-gateway-0.0.1-SNAPSHOT.jar
```

使用外部属性文件时：

```shell
java -jar openchat-getway/target/openchat-gateway-0.0.1-SNAPSHOT.jar --spring.config.additional-location=file:/etc/openchat/gateway.properties
```

Windows 可将位置替换为 `file:G:/openchat-config/gateway.properties`。配置中的证书路径必须对应实际运行环境；容器部署时还需挂载证书并映射监听端口。

**现有 Docker 构建文件尚未与 POM 对齐**：[`Dockerfile`](../Dockerfile) 使用 Java 17，并复制 `openchat-getway-0.0.1-SNAPSHOT.jar`；[`.dockerignore`](../.dockerignore) 也只放行这个文件名。当前 POM 要求 JDK 25，产物名为 `openchat-gateway-0.0.1-SNAPSHOT.jar`。使用 Docker 构建前，需要同步调整基础镜像与这两个文件中的产物路径。

## 验证与排查

MinIO 转发的定向回归命令：

```shell
mvn -f openchat-getway/pom.xml "-DskipTests=false" "-Dtest=MinioHostHeaderFilterTest" test
```

该测试使用临时回环 HTTP 服务，覆盖 Host 保留、桶查询、带签名参数的 PUT 请求以及其他路由的默认转发行为，不依赖真实 Nacos 或 MinIO。

| 现象 | 检查项 |
| --- | --- |
| Nacos 配置未加载 | 地址、账号权限、配置命名空间、Group 和 Data ID 是否与导入配置一致 |
| 启动时报证书错误 | 证书及私钥是否存在、可读、相互匹配，容器内路径是否正确 |
| 未知路径返回空白 404 | 检查路由是否覆盖该路径；这是当前未匹配路由处理器的行为 |
| 聊天请求无法转发 | Nacos 中是否有健康的 `openchat-server` 实例，发现命名空间是否一致，实例地址是否可达 |
| MinIO 返回 `SignatureDoesNotMatch` | 签名入口与访问入口是否一致，Host 中的端口、路径及查询参数是否被改写 |
| SSE 频繁断开或消息延迟 | 检查网关前置代理的空闲超时和响应缓冲，以及下游 SSE 心跳 |

样例还配置了 `gateway`、`health`、`info` Actuator 端点，网关端点访问级别为 `unrestricted`，健康详情为 `always`，并开启多项 DEBUG 日志。这些配置用于排障；对外部署时应限制管理入口可达范围，按需保留端点和日志级别。

本文根据当前源码和配置整理。构建、定向测试与真实环境接入验证是不同步骤，需在目标环境分别确认。
