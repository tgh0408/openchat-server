package com.openchat.rtc.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiveKit Server SDK 配置。
 */
@Data
@ConfigurationProperties(prefix = "openchat.rtc.livekit")
public class LiveKitProperties {
    private boolean enabled = true;
    private String serverUrl = "";
    private String apiKey = "";
    private String apiSecret = "";
    private long tokenTtlSeconds = 300;

    /** 媒体服务为可选配置；缺少连接或签名配置时不影响普通聊天服务启动。 */
    public boolean isConfigured() {
        return enabled && serverUrl != null && !serverUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank()
                && tokenTtlSeconds > 0;
    }
}
