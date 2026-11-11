package com.openchat.common.sse.controller;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.event.SseLoginEvent;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.common.sse.dto.SseConnectionType;
import com.openchat.common.sse.dto.SseMessageDto;
import com.openchat.common.utils.RequestHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 控制器
 *
 * @author Lion Li
 */
@RestController
@Slf4j
@ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/capi")
public class SseController {

    private final SseEmitterManager sseEmitterManager;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 建立 SSE 连接
     */
    @GetMapping(value = "${sse.path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam(value = "connectionType", defaultValue = "RECONNECT") SseConnectionType connectionType) {
        Long uid = RequestHolder.getUid();
        String token = RequestHolder.getToken();
        String ip = RequestHolder.getIp();
        log.info("SSE 连接请求已接收, uid={}, connectionType={}, ip={}", uid, connectionType, ip);
        SseEmitter emitter = sseEmitterManager.connect(uid, token, RequestHolder.get().getTerminal());
        if (connectionType != SseConnectionType.LOGIN) {
            return emitter;
        }
        try {
            applicationEventPublisher.publishEvent(new SseLoginEvent(this, uid, ip));
        } catch (RuntimeException exception) {
            // 附加事件的发布或监听失败不撤销已受理的 SSE 连接。
            log.warn("Failed to publish SSE login event, uid={}", uid, exception);
        }
        return emitter;
    }

    /**
     * 关闭 SSE 连接
     */
    @GetMapping(value = "${sse.path}/close")
    public ApiResult<Void> close() {
        Long uid = RequestHolder.getUid();
        sseEmitterManager.disconnect(uid, RequestHolder.getToken(), RequestHolder.get().getTerminal());
        log.info("SSE 显式关闭请求已处理, uid={}, ip={}", uid, RequestHolder.getIp());
        return ApiResult.success();
    }

    /**
     * 向特定用户发送消息
     *
     * @param userId 目标用户的 ID
     * @param msg    要发送的消息内容
     */
//    @GetMapping(value = "${sse.path}/send")
//    public ApiResult<Void> send(Long userId, String msg) {
//        SseMessageDto dto = new SseMessageDto();
//        dto.setUserIds(List.of(userId));
//        dto.setMessage(msg);
//        sseEmitterManager.publishMessage(dto);
//        return ApiResult.success();
//    }

    /**
     * 向所有用户发送消息
     *
     * @param msg 要发送的消息内容
     */
//    @GetMapping(value = "${sse.path}/sendAll")
//    public ApiResult<Void> send(String msg) {
//        sseEmitterManager.publishAll(msg);
//        return ApiResult.success();
//    }

}
