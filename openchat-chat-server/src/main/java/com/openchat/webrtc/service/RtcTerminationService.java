package com.openchat.webrtc.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.rtc.domain.RtcDevice;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.domain.RtcDeviceOwnership;
import com.openchat.rtc.domain.RtcEndReason;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.service.RtcOperationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单聊主动结束按最新状态执行，版本冲突及数据库锁冲突在完整回滚后重试。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcTerminationService {
    private static final int MAX_ATTEMPTS = 5;
    private final RtcCallService calls;
    private final RoomDao rooms;
    private final PlatformTransactionManager transactionManager;

    public RtcCallView terminate(String callId, Long uid, CallActionCommand command) {
        return terminate(callId, uid, command, false);
    }

    /** 仅供已验证恢复入口调用，在每次新事务中复核设备不匹配后结束单聊。 */
    public RtcCallView terminateForDeviceMismatch(String callId, Long uid, RtcDevice device) {
        return terminate(callId, uid, new CallActionCommand("device-recovery", 1, device), true);
    }

    private RtcCallView terminate(String callId, Long uid, CallActionCommand command, boolean deviceMismatch) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var groupCall = new AtomicBoolean();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                RtcCallView ended = transaction.execute(_ -> {
                    RtcCallView call = calls.get(callId, uid);
                    groupCall.set(call.calleeUid() == null);
                    if (call.state().terminal()) return call;
                    var room = rooms.get(call.roomId());
                    if (room == null) throw new BusinessException("房间号不存在");
                    if (deviceMismatch) {
                        if (room.getType() != RoomTypeEnum.FRIEND
                                || (call.state() != RtcCallState.ACCEPTED && call.state() != RtcCallState.CONNECTED)
                                || call.participants().stream().noneMatch(p -> p.uid().equals(uid)
                                && p.state() == RtcParticipantState.ACCEPTED))
                            throw new RtcOperationException(RtcOperationException.Reason.CALL_UNAVAILABLE);
                        RtcDeviceOwnership ownership = call.forDevice(uid, command.device()).deviceOwnership();
                        if (ownership == RtcDeviceOwnership.CURRENT_DEVICE)
                            throw new RtcOperationException(RtcOperationException.Reason.STATE_CONFLICT);
                        RtcEndReason reason = ownership == RtcDeviceOwnership.OTHER_DEVICE
                                ? RtcEndReason.DEVICE_MISMATCH : RtcEndReason.DEVICE_IDENTITY_MISSING;
                        return calls.forceHangup(callId, uid, reason);
                    }
                    if (call.callerUid().equals(uid) || call.participants().stream().anyMatch(p ->
                            p.uid().equals(uid) && p.state() == RtcParticipantState.ACCEPTED))
                        call.requireDevice(uid, command.device());
                    if (room.getType() == RoomTypeEnum.FRIEND)
                        return calls.forceHangup(callId, uid);
                    // 群聊挂断仅退出本人，同事务统计当前成员，为零时才结束整通。
                    return calls.hangup(callId, uid, command);
                });
                log.info("event={} callId={} uid={} reason={} committedVersion={}",
                        RtcAuditEvent.RTC_END_CONFIRMED, callId, uid, ended.endReason(), ended.version());
                return ended;
            } catch (RtcCallConflictException | ConcurrencyFailureException exception) {
                // 群个人操作携带成员版本，不能通过自动重试把旧退出应用到后一次重入。
                if (groupCall.get()) throw exception;
                log.warn("event={} callId={} uid={} attempt={} exceptionType={}",
                        RtcAuditEvent.RTC_FORCED_END_RETRY, callId, uid, attempt + 1, exception.getClass().getSimpleName());
                if (attempt == MAX_ATTEMPTS - 1) {
                    log.error("event={} callId={} uid={}", RtcAuditEvent.RTC_FORCED_END_FAILED, callId, uid, exception);
                    throw new RtcOperationException(RtcOperationException.Reason.TERMINATION_FAILED);
                }
            }
        }
        throw new IllegalStateException("结束通话重试未返回结果");
    }
}
