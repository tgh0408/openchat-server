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


}
