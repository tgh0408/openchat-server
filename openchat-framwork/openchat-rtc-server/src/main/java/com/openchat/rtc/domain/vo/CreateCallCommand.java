package com.openchat.rtc.domain.vo;

import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.domain.RtcDevice;

import java.util.List;

/**
 * 创建呼叫命令。
 */
public record CreateCallCommand(
        Long roomId,
        RtcMediaMode mediaMode,
        String clientRequestId,
        List<Long> participantUids,
        RtcDevice device) {
    public CreateCallCommand(Long roomId, RtcMediaMode mediaMode, String clientRequestId, List<Long> participantUids) {
        this(roomId, mediaMode, clientRequestId, participantUids, null);
    }
    public CreateCallCommand(Long roomId, RtcMediaMode mediaMode, String clientRequestId) {
        this(roomId, mediaMode, clientRequestId, null);
    }

    public CreateCallCommand {
        if (roomId == null || roomId <= 0 || mediaMode == null
                || clientRequestId == null || clientRequestId.isBlank()
                || clientRequestId.length() > 128 || clientRequestId.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("创建呼叫参数无效");
        }
        if (participantUids != null
                && (participantUids.isEmpty()
                || participantUids.stream().anyMatch(uid -> uid == null || uid <= 0)
                || participantUids.stream().distinct().count() != participantUids.size())) {
            throw new IllegalArgumentException("呼叫参与者无效");
        }
        participantUids = participantUids == null ? null : List.copyOf(participantUids);
    }
}
