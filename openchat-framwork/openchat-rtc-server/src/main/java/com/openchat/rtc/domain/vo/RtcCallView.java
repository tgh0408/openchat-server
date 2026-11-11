package com.openchat.rtc.domain.vo;

import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcMediaMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.openchat.rtc.domain.RtcDevice;
import com.openchat.rtc.domain.RtcDeviceOwnership;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.service.RtcOperationException;

/**
 * 对外返回的呼叫状态；协议枚举固定为名称，不受全局数字枚举配置影响。
 */
public record RtcCallView(
        String callId,
        Long roomId,
        Long callerUid,
        Long calleeUid,
        RtcMediaMode mediaMode,
        RtcCallState state,
        Integer version,
        Instant expiresAt,
        String endReason,
        Instant createdAt,
        Instant updatedAt,
        Instant acceptedAt,
        Instant connectedAt,
        Instant endedAt,
        Long durationSeconds,
        Long endedByUid,
        Long summaryMessageId,
        List<RtcCallParticipantView> participants,
        RtcDeviceOwnership deviceOwnership,
        @JsonIgnore Map<Long, RtcDevice> participantDevices) {

    /** 内部视图可保留设备快照，HTTP 只公开当前请求设备的归属枚举。 */
    public RtcCallView(
            String callId, Long roomId, Long callerUid, Long calleeUid, RtcMediaMode mediaMode,
            RtcCallState state, Integer version, Instant expiresAt, String endReason,
            Instant createdAt, Instant updatedAt, Instant acceptedAt, Instant connectedAt,
            Instant endedAt, Long durationSeconds, Long endedByUid, Long summaryMessageId,
            List<RtcCallParticipantView> participants) {
        this(callId, roomId, callerUid, calleeUid, mediaMode, state, version, expiresAt,
                endReason, createdAt, updatedAt, acceptedAt, connectedAt, endedAt,
                durationSeconds, endedByUid, summaryMessageId, participants,
                RtcDeviceOwnership.UNKNOWN, Map.of());
    }

    /** 按当前已验证身份投影归属，不暴露其他参与者的设备标识。 */
    public RtcCallView forDevice(Long uid, RtcDevice device) {
        RtcDevice owner = participantDevices.get(uid);
        RtcDeviceOwnership ownership;
        if (owner != null) {
            ownership = owner.equals(device) ? RtcDeviceOwnership.CURRENT_DEVICE : RtcDeviceOwnership.OTHER_DEVICE;
        } else if (participants.stream().anyMatch(p -> p.uid().equals(uid)
                && p.state() == RtcParticipantState.INVITED)) {
            ownership = RtcDeviceOwnership.UNASSIGNED;
        }else {
            ownership = RtcDeviceOwnership.UNKNOWN;
        }
        return new RtcCallView(callId, roomId, callerUid, calleeUid, mediaMode, state, version,
                expiresAt, endReason, createdAt, updatedAt, acceptedAt, connectedAt, endedAt,
                durationSeconds, endedByUid, summaryMessageId, participants, ownership, participantDevices);
    }

    /** 拒绝另一设备或缺失归属的媒体/结束操作，调用方须先校验参与权限。 */
    public void requireDevice(Long uid, RtcDevice device) {
        RtcDevice owner = participantDevices.get(uid);
        if (owner == null || device == null)
            throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
        if (!owner.equals(device))
            throw new RtcOperationException(RtcOperationException.Reason.ACCEPTED_ELSEWHERE);
    }
}
