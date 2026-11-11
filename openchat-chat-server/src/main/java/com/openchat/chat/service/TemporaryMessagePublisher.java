package com.openchat.chat.service;

import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 占位及更新推送通过切面登记可靠调用，与业务变更一起提交。 */
@Service
@RequiredArgsConstructor
public class TemporaryMessagePublisher {
    private final MQProducer producer;

    @SecureInvoke(maxRetryTimes = 10)
    public void publish(MsgSendMessageDTO message) {
        producer.sendSecureMsg(MQConstant.SEND_MSG_TOPIC, message);
    }
}
