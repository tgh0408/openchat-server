package com.openchat.transaction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** LiteTopic 推送配置。 */
@Data
@ConfigurationProperties("openchat.chat.push.lite")
public class LiteTopicPushProperties {

    private boolean enabled;

    private String endpoints = "";

    private String parentTopic = "sse_push_lite";

    private String consumerGroup = "sse_push_lite";

    private String nodeId = "";

    private String accessKey = "";

    private String secretKey = "";

    private boolean ssl = false;

    private int subscriptionExecutorThreads = 2;
}
