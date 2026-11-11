package com.openchat.common.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
public abstract class AbstractRocketQListener<T> implements RocketMQListener {
    @Autowired
    private JsonMapper jsonMapper;

    private final Class<?> messageType;

    protected AbstractRocketQListener() {
        messageType = ResolvableType.forClass(getClass())
                .as(AbstractRocketQListener.class)
                .getGeneric(0)
                .resolve();
    }

    @Override
    public ConsumeResult consume(MessageView message) {
        try {
            T dto = jsonMapper.readValue(
                    StandardCharsets.UTF_8.decode(
                            message.getBody().duplicate()).toString(),
                    jsonMapper.getTypeFactory().constructType(messageType));
            Assert.notNull(dto, "MQ message body must not be null");
            onMessage(dto);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException exception) {
            log.error("RocketMQ consumption failed, messageId={}",
                    message.getMessageId(), exception);
            return ConsumeResult.FAILURE;
        }
    }

    protected abstract void onMessage(T dto);
}
