package com.openchat.user.consumer;

import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.chat.service.SsePushService;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.rocketmq.AbstractRocketQListener;
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
 * SSE 推送消息的 RocketMQ 消费适配器。
 */
@Component
// 每个 SSE 节点使用独立消费组，确保事件到达持有连接的节点。
@RocketMQMessageListener(
        topic = MQConstant.SSE_TOPIC,
        consumerGroup = MQConstant.SSE_GROUP + "-${rocketmq.lite.chatnode:chat-node-1}",
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}")
@RequiredArgsConstructor
@Slf4j
public class PushConsumer extends AbstractRocketQListener<PushMessageDTO> {

    private final SsePushService ssePushService;

    @Override
    protected void onMessage(PushMessageDTO dto) {
        ssePushService.push(dto);
    }
}
