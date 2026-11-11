package com.openchat.webrtc.consumer;

import com.openchat.common.constant.MQConstant;
import com.openchat.common.rocketmq.AbstractRocketQListener;
import com.openchat.webrtc.domain.dto.RtcPushMessage;
import com.openchat.webrtc.service.RtcSsePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * RTC 事件跨节点消费入口。
 */
@Component
// RTC 推送同样需要到达所有 SSE 节点，不能共用负载均衡消费组。
@RocketMQMessageListener(
        topic = MQConstant.RTC_SSE_TOPIC,
        consumerGroup = MQConstant.RTC_SSE_GROUP + "-${rocketmq.lite.chatnode:chat-node-1}",
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}")
@RequiredArgsConstructor
@Slf4j
public class RtcPushConsumer extends AbstractRocketQListener<RtcPushMessage> {
    private final RtcSsePushService pushService;

    @Override
    protected void onMessage(RtcPushMessage dto) {
        pushService.push(dto);
    }
}
