package com.openchat.webrtc.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.RequestHolder;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.CreateCallCommand;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.domain.RtcJoinCredential;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.rtc.service.RtcOperationException;
import com.openchat.webrtc.domain.vo.req.CreateRtcCallReq;
import com.openchat.webrtc.domain.vo.req.RtcCallActionReq;
import com.openchat.webrtc.service.RtcLiveKitService;
import com.openchat.webrtc.service.RtcMediaService;
import com.openchat.webrtc.service.RtcRecoveryService;
import com.openchat.webrtc.service.RtcTerminationService;
import com.openchat.webrtc.service.RtcDialService;
import com.openchat.webrtc.service.RtcDeviceContext;
import com.openchat.webrtc.domain.vo.resp.RtcOngoingCallView;
import java.util.List;
import com.openchat.webrtc.domain.vo.req.RtcMediaSignalReq;
import com.openchat.webrtc.domain.vo.resp.RtcMediaView;
import com.openchat.webrtc.domain.vo.req.RtcVideoActionReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RTC 呼叫和信令接口。鉴权由现有 TokenInterceptor 统一完成。
 */
@RestController
@RequestMapping("/capi/rtc")
@RequiredArgsConstructor
@Slf4j
public class RtcController {
    private final RtcCallService callService;
    private final RtcLiveKitService liveKitService;
    private final RtcMediaService mediaService;
    private final RtcRecoveryService recoveryService;
    private final RtcTerminationService terminationService;
    private final RtcDialService dialService;

    /** 安卓与 PC 共用：有效群成员查询该群未结束通话，无通话返回 data:null。 */
    @GetMapping("/rooms/{roomId}/active")
    public ApiResult<RtcCallView> activeGroup(@PathVariable Long roomId) {
        RtcCallView call = dialService.activeGroup(RequestHolder.getUid(), roomId);
        return ApiResult.success(call == null ? null : view(call));
    }

    /** 呼出前检查本人所有尚未结束的参与记录，包含响铃与群聊。 */
    @GetMapping("/calls/active")
    public ApiResult<List<RtcCallView>> active() {
        return ApiResult.success(dialService.active(RequestHolder.getUid()).stream().map(this::view).toList());
    }

    /** 查询本人尚未结束的单聊；不包含响铃邀请或群聊。 */
    @GetMapping("/calls/ongoing")
    public ApiResult<List<RtcOngoingCallView>> ongoing() {
        return ApiResult.success(recoveryService.ongoing(RequestHolder.getUid()).stream()
                .map(item -> new RtcOngoingCallView(view(item.call()), item.peerName())).toList());
    }

    /** 用户确认重连后复用原呼叫，媒体改走既有 SFU 通道。 */
    @PostMapping("/calls/{callId}/reconnect")
    public ApiResult<RtcCallView> reconnect(@PathVariable String callId) {
        return ApiResult.success(view(recoveryService.reconnect(callId, RequestHolder.getUid())));
    }

    @PostMapping("/calls")
    public ApiResult<RtcCallView> create(@Valid @RequestBody CreateRtcCallReq request) {
        return ApiResult.success(view(dialService.create(
                RequestHolder.getUid(),
                new CreateCallCommand(
                        request.getRoomId(),
                        request.getMediaMode(),
                        request.getClientRequestId(),
                        request.getParticipantUids(), RtcDeviceContext.current()),
                request.getReplaceCallIds())));
    }

    @GetMapping("/calls/{callId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<RtcCallView> get(@PathVariable String callId) {
        return ApiResult.success(view(callService.get(callId, RequestHolder.getUid())));
    }

    /** 单聊接受邀请；群聊当前有效成员可直接加入，无须预先受邀。 */
    @PostMapping("/calls/{callId}/accept")
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResult<RtcCallView> accept(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        Long uid = RequestHolder.getUid();
        CallActionCommand command = action(request);
        String logCallId = StringUtils.truncate(StringUtils.replaceChars(callId, "\r\n", "__"), 128);
        log.info("event={} callId={} uid={} terminal={} deviceFingerprint={} clientRequestId={}",
                RtcAuditEvent.RTC_ACCEPT_REQUESTED, logCallId, uid, command.device().terminal(),
                command.device().fingerprint(),
                StringUtils.truncate(StringUtils.replaceChars(command.clientRequestId(), "\r\n", "__"), 128));
        try {
            RtcCallView result = callService.accept(callId, uid, command).forDevice(uid, command.device());
            // 方法返回时事务尚未提交，只有提交回调可以记录接听确认。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("event={} callId={} uid={} terminal={} deviceFingerprint={} committedVersion={}",
                            RtcAuditEvent.RTC_ACCEPT_CONFIRMED, logCallId, uid, command.device().terminal(),
                            command.device().fingerprint(), result.version());
                }
            });
            return ApiResult.success(result);
        } catch (RtcOperationException exception) {
            log.info("event={} callId={} uid={} terminal={} deviceFingerprint={} reason={}",
                    RtcAuditEvent.RTC_ACCEPT_REJECTED, logCallId, uid, command.device().terminal(),
                    command.device().fingerprint(), exception.reason());
            throw exception;
        }
    }

    /** 发起端到一分钟主动请求断开未接听邀请，服务端重新校验截止时间和最新成员状态。 */
    @PostMapping("/calls/{callId}/expire")
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResult<RtcCallView> expire(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        return ApiResult.success(view(callService.expireInvitations(
                callId,
                RequestHolder.getUid(),
                action(request))));
    }

    @PostMapping("/calls/{callId}/reject")
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResult<RtcCallView> reject(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        return ApiResult.success(view(callService.reject(
                callId,
                RequestHolder.getUid(),
                action(request))));
    }

    @PostMapping("/calls/{callId}/cancel")
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResult<RtcCallView> cancel(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        return ApiResult.success(view(callService.cancel(
                callId,
                RequestHolder.getUid(),
                action(request))));
    }

    /** 单聊结束优先；事务由结束服务逐次创建，冲突不能在原快照内空转。 */
    @PostMapping("/calls/{callId}/hangup")
    public ApiResult<RtcCallView> hangup(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        return ApiResult.success(view(terminationService.terminate(
                callId,
                RequestHolder.getUid(),
                action(request))));
    }

    @PostMapping("/calls/{callId}/leave")
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ApiResult<RtcCallView> leave(
            @PathVariable String callId,
            @Valid @RequestBody RtcCallActionReq request) {
        return ApiResult.success(view(callService.leave(
                callId,
                RequestHolder.getUid(),
                action(request))));
    }

    @PostMapping("/calls/{callId}/token")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<RtcJoinCredential> token(@PathVariable String callId) {
        mediaService.useSfu(callId, RequestHolder.getUid());
        return ApiResult.success(liveKitService.issueToken(callId, RequestHolder.getUid()));
    }

    @GetMapping("/calls/{callId}/media")
    public ApiResult<RtcMediaView> media(@PathVariable String callId) {
        return ApiResult.success(mediaService.get(callId, RequestHolder.getUid()));
    }

    @PostMapping("/calls/{callId}/media")
    public ApiResult<RtcMediaView> signal(
            @PathVariable String callId, @Valid @RequestBody RtcMediaSignalReq request) {
        return ApiResult.success(mediaService.signal(callId, RequestHolder.getUid(), request));
    }

    @PostMapping("/calls/{callId}/video")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Boolean> video(
            @PathVariable String callId,
            @Valid @RequestBody RtcVideoActionReq request) {
        return ApiResult.success(liveKitService.setVideoEnabled(
                callId,
                RequestHolder.getUid(),
                request.getEnabled()));
    }

    /** 所有 HTTP 快照只公开当前设备的归属，设备原始映射不序列化。 */
    private RtcCallView view(RtcCallView call) {
        return call.forDevice(RequestHolder.getUid(), RtcDeviceContext.current());
    }

    /** 操作身份由服务端上下文注入，不接受页面传入的设备或 uid。 */
    private CallActionCommand action(RtcCallActionReq request) {
        return new CallActionCommand(request.getClientRequestId(), request.getExpectedVersion(),
                RtcDeviceContext.current());
    }
}
