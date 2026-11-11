package com.openchat.webrtc.service;

import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.rtc.service.RtcCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GlobalSchedule 调用的兜底执行器；成员条件更新裁决竞争，故障回滚后下轮重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RtcInvitationExpiryTask {
    private final RtcCallRepository repository;
    private final RtcCallService callService;
    private final PlatformTransactionManager transactionManager;

    public void expireInvitations() {
        for (String callId : repository.findDueInvitations(200)) {
            try {
                var transaction = new TransactionTemplate(transactionManager);
                transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                transaction.executeWithoutResult(status -> callService.expire(callId));
            } catch (Exception exception) {
                log.warn("RTC 邀请到期处理失败，保留下轮重试，callId={}", callId, exception);
            }
        }
    }
}
