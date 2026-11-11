package com.openchat.webrtc.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.JsonUtils;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.domain.RtcEventType;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.rtc.port.RtcIdGenerator;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.webrtc.config.RtcP2pProperties;
import com.openchat.webrtc.domain.entity.RtcMediaSession;
import com.openchat.webrtc.domain.enums.RtcMediaSignalTypeEnum;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import com.openchat.webrtc.domain.vo.req.RtcMediaSignalReq;
import com.openchat.webrtc.domain.vo.resp.RtcMediaView;
import com.openchat.webrtc.dao.RtcMediaDao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 媒体协商、不可逆回退及摄像头状态；复用数据库版本更新与 Redis 原子操作协调多节点。 */
@Service
@RequiredArgsConstructor
public class RtcMediaService {
    private final RtcCallService calls;
    private final RoomDao rooms;
    private final RtcMediaDao mediaDao;
    private final RtcP2pProperties properties;
    private final RtcEventPublisher events;
    private final RtcIdGenerator ids;
    private final RtcCameraStateService cameras;

    @Transactional(rollbackFor = Exception.class)
    public RtcMediaView get(String callId, Long uid) {
        RtcCallView call = requireAccepted(callId, uid);
        return view(load(call, uid), call);
    }

    @Transactional(rollbackFor = Exception.class)
    public RtcMediaView signal(String callId, Long uid, RtcMediaSignalReq request) {
        RtcCallView call = requireAccepted(callId, uid);
        RtcMediaSession media = load(call, uid);
        // 摄像头状态独立于 SDP，回退至服务器转发后仍需同步。
        if (request.getKind() == RtcMediaSignalTypeEnum.CAMERA) {
            updateCamera(call, uid, request);
            return view(media, call);
        }
        // 已进入服务器转发模式时，不再接受点对点协商覆盖现有快照。
        if (media.getTransport() == RtcMediaTransportEnum.SFU) return view(media, call);
        int changed;
        switch (request.getKind()) {
            // 不可逆回退至服务器转发，并清除点对点协商数据。
            case FALLBACK -> changed = mediaDao.fallback(callId, media.getVersion());
            // 仅主叫可提交提议；相同请求重放直接返回，冲突提议拒绝覆盖。
            case OFFER -> {
                if (!call.callerUid().equals(uid)) throw new BusinessException("只有主叫可以发送 Offer");
                requireSdp(request.getSdp());
                if (media.getOfferId() != null) {
                    if (!Objects.equals(media.getOfferId(), request.getClientRequestId())
                            || !Objects.equals(media.getOfferSdp(), request.getSdp()))
                        throw new BusinessException("协商已存在，请切换服务器通道");
                    return view(media, call);
                }
                changed = mediaDao.offer(callId, media.getVersion(), request.getClientRequestId(), request.getSdp());
            }
            // 仅被叫可应答，且必须绑定当前提议，不能覆盖已有应答。
            case ANSWER -> {
                if (!call.calleeUid().equals(uid)) throw new BusinessException("只有被叫可以发送 Answer");
                requireSdp(request.getSdp());
                if (media.getOfferId() == null || !Objects.equals(media.getOfferId(), request.getOfferId()))
                    throw new BusinessException("协商已失效");
                if (media.getAnswerSdp() != null) {
                    if (!Objects.equals(media.getAnswerSdp(), request.getSdp()))
                        throw new BusinessException("应答已存在，请切换服务器通道");
                    return view(media, call);
                }
                changed = mediaDao.answer(callId, media.getVersion(), request.getOfferId(), request.getSdp());
            }
            default -> throw new BusinessException("无效的媒体信令");
        }
        if (changed != 1) throw new BusinessException("媒体状态已变化，请重试");
        media = mediaDao.selectById(callId);
        publish(call, uid, media.getVersion());
        return view(media, call);
    }

    /** 旧客户端直接申请 Token 也先确认 SFU，防止新旧客户端进入不同传输通道。 */
    @Transactional(rollbackFor = Exception.class)
    public void useSfu(String callId, Long uid) {
        RtcMediaSignalReq request = new RtcMediaSignalReq();
        request.setKind(RtcMediaSignalTypeEnum.FALLBACK);
        signal(callId, uid, request);
    }

    private RtcCallView requireAccepted(String callId, Long uid) {
        // 读取当前状态，后续状态变更由 version 条件更新完成并发控制。
        RtcCallView call = calls.get(callId, uid);
        call.requireDevice(uid, RtcDeviceContext.current());
        if ((call.state() != RtcCallState.ACCEPTED && call.state() != RtcCallState.CONNECTED)
                || call.participants().stream().noneMatch(p ->
                p.uid().equals(uid) && p.state() == RtcParticipantState.ACCEPTED))
            throw new BusinessException("当前呼叫未接听或已结束");
        return call;
    }

    private RtcMediaSession load(RtcCallView call, Long uid) {
        RtcMediaSession media = mediaDao.selectById(call.callId());
        if (media != null) {
            // 旧回退实现可能已写入 SFU 却保留 SDP；授权读取时修复，不放宽客户端协议。
            if (media.getTransport() == RtcMediaTransportEnum.SFU
                    && (media.getOfferId() != null || media.getOfferSdp() != null || media.getAnswerSdp() != null)) {
                if (mediaDao.fallback(call.callId(), media.getVersion()) != 1)
                    throw new BusinessException("媒体状态已变化，请重试");
                media = mediaDao.selectById(call.callId());
                publish(call, uid, media.getVersion());
            }
            return media;
        }
        Room room = rooms.get(call.roomId());
        boolean direct = properties.isEnabled() && room != null
                && room.getType() == RoomTypeEnum.FRIEND
                && call.participants().size() == 2;
        if (mediaDao.insert(call.callId(), direct ? RtcMediaTransportEnum.P2P : RtcMediaTransportEnum.SFU) != 1)
            throw new BusinessException("媒体状态创建失败");
        return mediaDao.selectById(call.callId());
    }

    private RtcMediaView view(RtcMediaSession media, RtcCallView call) {
        List<String> urls = properties.getStunUrls().stream()
                .filter(url -> url != null && url.matches("stuns?:[^\\s/@?#]{1,240}"))
                .limit(4).toList();
        return new RtcMediaView(call.callId(), media.getTransport(), media.getVersion(), media.getOfferId(),
                media.getOfferSdp(), media.getAnswerSdp(), urls,
                call.mediaMode() == RtcMediaMode.VIDEO ? cameras.get(call.callId()) : Map.of());
    }

    /** 摄像头通知独立于 SDP，SFU 与 P2P 均可上报，身份仅取当前授权。 */
    private void updateCamera(RtcCallView call, Long uid, RtcMediaSignalReq request) {
        if (call.mediaMode() != RtcMediaMode.VIDEO || request.getEnabled() == null
                || request.getExpectedCameraVersion() == null || request.getExpectedCameraVersion() < 0
                || request.getExpectedCameraVersion() >= 9_007_199_254_740_991L) {
            throw new BusinessException("摄像头状态参数无效");
        }
        var state = cameras.update(call.callId(), uid, request.getEnabled(), request.getExpectedCameraVersion());
        for (var participant : call.participants()) {
            if (participant.state() != RtcParticipantState.ACCEPTED) continue;
            events.publish(new RtcEvent(ids.nextId(), RtcEventType.RTC_CAMERA_UPDATED,
                    call.callId(), call.roomId(), uid, participant.uid(), call.version(),
                    Long.toString(state.version()), Instant.now(), JsonUtils.toStr(state)));
        }
    }

    private void requireSdp(String sdp) {
        if (sdp == null || sdp.length() > 60000 || !sdp.startsWith("v=0\r\n")
                || !sdp.contains("\r\nm=audio ") || sdp.contains("\u0000"))
            throw new BusinessException("媒体描述无效");
    }

    private void publish(RtcCallView call, Long uid, long version) {
        for (var participant : call.participants()) {
            events.publish(new RtcEvent(ids.nextId(), RtcEventType.RTC_MEDIA_UPDATED, call.callId(), call.roomId(),
                    uid, participant.uid(), call.version(), Long.toString(version), Instant.now(), null));
        }
    }
}
