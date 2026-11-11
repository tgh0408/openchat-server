package com.openchat.common.sse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 配置项
 *
 * @author Lion Li
 */
@Data
@ConfigurationProperties("sse")
public class SseProperties {

    private Boolean enabled;

    /**
     * 路径
     */
    private String path;

    private int pushCoreThreads = 4;

    private int pushMaxThreads = 16;

    private int pushQueueCapacity = 1000;

    private int heartbeatIntervalSeconds = 15;
}
