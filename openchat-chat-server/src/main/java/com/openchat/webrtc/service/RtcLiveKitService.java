package com.openchat.webrtc.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.RtcJoinCredential;
import com.openchat.rtc.service.LiveKitTokenService;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.user.dao.UserDao;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.webrtc.constant.LiveKitRoomKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * RTC 业务权限与 LiveKit Token 适配层。
 */
@Service
@RequiredArgsConstructor
public class RtcLiveKitService {
    private final RtcCallService callService;
    private final UserDao userDao;
    private final LiveKitTokenService tokenService;
    private final RtcVideoSeatService videoSeatService;

    public RtcJoinCredential issueToken(String callId, Long uid) {
        RtcCallView call = callService.get(callId, uid);
        call.requireDevice(uid, RtcDeviceContext.current());
        if (call.state() != RtcCallState.ACCEPTED && call.state() != RtcCallState.CONNECTED) {
            throw new BusinessException("当前呼叫尚未允许加入 LiveKit 房间");
        }
        boolean accepted = call.participants().stream().anyMatch(participant ->
                participant.uid().equals(uid) && participant.state() == RtcParticipantState.ACCEPTED);
        if (!accepted) {
            throw new BusinessException("当前用户尚未接听或邀请已失效");
        }
        User user = userDao.get(IdTypeEnum.ID, uid,"用户不存在");
        boolean videoAllowed = RtcMediaMode.VIDEO.equals(call.mediaMode());
        if (videoAllowed && call.calleeUid() == null) {
            videoAllowed = videoSeatService.acquire(call.callId(), uid);
        }
        return tokenService.issue(
                LiveKitRoomKey.roomKey + call.callId(),
                uid.toString(),
                user.getName(),
                videoAllowed);
    }

    /**
     * 领取或释放群视频席位。单聊视频不受群视频席位限制。
     */
    public boolean setVideoEnabled(String callId, Long uid, boolean enabled) {
        RtcCallView call = callService.get(callId, uid);
        call.requireDevice(uid, RtcDeviceContext.current());
        if (enabled && (call.state().terminal() || call.participants().stream().noneMatch(p ->
                p.uid().equals(uid) && p.state() == RtcParticipantState.ACCEPTED)))
            throw new BusinessException("当前用户尚未接听或通话已结束");
        if (!"VIDEO".equals(call.mediaMode().name()) || call.calleeUid() != null) {
            return enabled;
        }
        if (enabled) {
            return videoSeatService.acquire(call.callId(), uid);
        }
        videoSeatService.release(call.callId(), uid);
        return false;
    }
}
