package com.openchat.webrtc.service;

import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.service.ChatService;
import com.openchat.common.domain.enums.MsgSourceEnum;
import com.openchat.transaction.annotation.SecureInvoke;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * RTC 摘要的事务后可靠发送器。
 */
@Service
@RequiredArgsConstructor
public class RtcCallSummaryPublisher {
    private final ChatService chatService;

    /**
     * 事务提交后发送 RTC 摘要；重试复用已落库消息，失败由本条可靠调用记录继续重试。
     */
    @SecureInvoke(async = true, maxRetryTimes = 10)
    public void publish(ChatMessageReq request, Long callerUid) {
        chatService.sendMsg(request, callerUid, MsgSourceEnum.SERVER);
    }
}
