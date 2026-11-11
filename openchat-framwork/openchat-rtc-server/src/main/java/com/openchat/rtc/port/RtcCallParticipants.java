package com.openchat.rtc.port;

import java.util.List;

/**
 * 创建呼叫时由业务层解析出的参与者。
 */
public record RtcCallParticipants(
        Long roomId,
        Long callerUid,
        Long calleeUid,
        List<Long> participantUids) {
    public RtcCallParticipants(Long roomId, Long callerUid, Long calleeUid) {
        this(roomId, callerUid, calleeUid, List.of(callerUid, calleeUid));
    }

    public RtcCallParticipants {
        if (roomId == null || roomId <= 0 || callerUid == null || callerUid <= 0
                || (calleeUid != null && (calleeUid <= 0 || callerUid.equals(calleeUid)))) {
            throw new IllegalArgumentException("呼叫参与者无效");
        }
        if (participantUids == null || participantUids.size() < 2
                || !participantUids.contains(callerUid)
                || (calleeUid != null && !participantUids.contains(calleeUid))
                || participantUids.stream().anyMatch(uid -> uid == null || uid <= 0)
                || participantUids.stream().distinct().count() != participantUids.size()) {
            throw new IllegalArgumentException("RTC 参与者数量或 UID 无效");
        }
        participantUids = List.copyOf(participantUids);
    }
}
