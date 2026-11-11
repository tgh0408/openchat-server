package com.openchat.webrtc.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.RtcDeviceOwnership;
import com.openchat.rtc.domain.RtcEndReason;
import com.openchat.rtc.service.RtcOperationException;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.webrtc.dao.RtcCallDao;
import com.openchat.webrtc.dao.RtcMediaDao;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import com.openchat.webrtc.domain.vo.resp.RtcOngoingCallView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/** 登录后的单聊恢复；发现通话只读，用户确认后才重建媒体连接。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcRecoveryService {
    private final RtcCallDao callDao;
    private final RtcCallService calls;
    private final RoomDao rooms;
    private final UserDao users;
    private final RtcMediaService media;
    private final RtcMediaDao mediaDao;
    private final PlatformTransactionManager transactionManager;
    private final RtcTerminationService termination;

    public List<RtcOngoingCallView> ongoing(Long uid) {
        List<RtcOngoingCallView> result = new ArrayList<>();
        for (var candidate : callDao.findOngoingCandidates(uid)) {
            // 先排除真实群房间，避免已退群候选的权限异常阻塞本人单聊发现。
            if (!directRoom(candidate.getRoomId())) continue;
            RtcCallView call = calls.get(candidate.getCallId(), uid);
            if (!accepted(call, uid)) continue;
            Long peerUid = call.callerUid().equals(uid) ? call.calleeUid() : call.callerUid();
            var peer = users.get(IdTypeEnum.ID, peerUid);
            String name = peer == null || peer.getName() == null || peer.getName().isBlank()
                    ? peerUid.toString() : peer.getName();
            result.add(new RtcOngoingCallView(call, name));
        }
        return List.copyOf(result);
    }

    /** 原呼叫身份、媒体类型和接听时间保持不变；强杀后的旧 SDP 不可重用。 */
    public RtcCallView reconnect(String callId, Long uid) {
        var device = RtcDeviceContext.current();
        log.info("event={} callId={} uid={} terminal={} deviceFingerprint={}",
                RtcAuditEvent.RTC_RECOVERY_REQUESTED, callId, uid, device.terminal(), device.fingerprint());
        try {
            RtcCallView result = restore(callId, uid);
            log.info("event={} callId={} uid={} committedVersion={}",
                    RtcAuditEvent.RTC_RECOVERY_COMMITTED, callId, uid, result.version());
            return result;
        } catch (DeviceMismatch exception) {
            log.warn("event={} callId={} uid={} terminal={} deviceFingerprint={} ownership={}",
                    RtcAuditEvent.RTC_RECOVERY_DEVICE_MISMATCH, callId, uid, device.terminal(), device.fingerprint(), exception.ownership);
            RtcCallView ended = termination.terminateForDeviceMismatch(callId, uid, device);
            RtcOperationException.Reason reason = switch (RtcEndReason.fromValue(ended.endReason())) {
                case DEVICE_MISMATCH -> RtcOperationException.Reason.RECOVERY_DEVICE_MISMATCH;
                case DEVICE_IDENTITY_MISSING -> RtcOperationException.Reason.RECOVERY_DEVICE_UNKNOWN;
                case null, default -> RtcOperationException.Reason.CALL_UNAVAILABLE;
            };
            // 结束事务已经提交，业务失败必须在事务之外返回。
            throw new RtcOperationException(reason);
        }
    }

    private RtcCallView restore(String callId, Long uid) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return transaction.execute(_ -> {
                RtcCallView call = requireRecoverable(callId, uid);
                // 普通条件 UPDATE 与 @Version 在同一事务内协调挂断，不使用显式悲观锁。
                if (!callDao.claimReconnect(callId, call.version(), call.state()))
                    throw new RtcCallConflictException();
                media.useSfu(callId, uid);
                return calls.get(callId, uid);
            });
        } catch (RtcCallConflictException | ConcurrencyFailureException exception) {
            // 失败事务已回滚；新事务读取竞争方提交的结果，不能复用原事务的旧快照。
            transaction.setReadOnly(true);
            return transaction.execute(status -> {
                RtcCallView call = requireRecoverable(callId, uid);
                var snapshot = mediaDao.selectById(callId);
                if (snapshot == null || snapshot.getTransport() != RtcMediaTransportEnum.SFU
                        || snapshot.getOfferId() != null || snapshot.getOfferSdp() != null
                        || snapshot.getAnswerSdp() != null)
                    throw new BusinessException("通话状态已变化，请重试");
                return call;
            });
        }
    }

    private RtcCallView requireRecoverable(String callId, Long uid) {
        RtcCallView call = calls.get(callId, uid);
        if (!directRoom(call.roomId()) || !accepted(call, uid))
            throw new BusinessException("单聊通话已结束或无法恢复");
        RtcDeviceOwnership ownership = call.forDevice(uid, RtcDeviceContext.current()).deviceOwnership();
        if (ownership != RtcDeviceOwnership.CURRENT_DEVICE)
            throw new DeviceMismatch(ownership);
        return call;
    }

    /** 内部判断结果在事务回滚后才交给独立结束服务，不提前对外声称已挂断。 */
    private static final class DeviceMismatch extends RuntimeException {
        private final RtcDeviceOwnership ownership;
        private DeviceMismatch(RtcDeviceOwnership ownership) {
            this.ownership = ownership;
        }
    }

    private boolean accepted(RtcCallView call, Long uid) {
        if (call.state() != RtcCallState.ACCEPTED && call.state() != RtcCallState.CONNECTED)
            return false;
        return call.participants().size() == 2 && call.participants().stream().anyMatch(
                participant -> participant.uid().equals(uid)
                        && participant.state() == RtcParticipantState.ACCEPTED);
    }

    private boolean directRoom(Long roomId) {
        var room = rooms.get(roomId);
        return room != null && room.getType() == RoomTypeEnum.FRIEND;
    }
}
