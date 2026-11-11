package com.openchat.webrtc.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.openchat.webrtc.service.RtcWebhookService;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** LiveKit 服务端回调；身份由 SDK 验签，不使用用户 JWT 和设备上下文。 */
@RequestMapping("/capi/livekit")
@RestController
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "openchat.rtc.livekit", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LiveKitWebhookController {
    private final WebhookReceiver receiver;
    private final RtcWebhookService webhookService;

    /** 原始 UTF-8 正文参与摘要校验，不能经过 JSON 转换后再验签。 */
    @PostMapping(value = "/callback", consumes = "application/webhook+json")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody byte[] body) throws Exception {
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WebhookEvent event;
        try {
            String rawBody = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            event = receiver.receive(rawBody, authorization);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            // 不输出签名头、正文或 SDK 异常消息。
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception exception) {
            // 此处只有正文解码和 SDK 本地解析；两者的输入格式错误均为 IOException。
            if (exception instanceof IOException) {
                return ResponseEntity.badRequest().build();
            }
            throw exception;
        }
        webhookService.handle(event);
        return ResponseEntity.noContent().build();
    }

    /** 回调必须返回真实 HTTP 失败状态，避免全局 ApiResult 将处理失败包装为 200。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> failure(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException) {
            return ResponseEntity.badRequest().build();
        }
        if (exception instanceof ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode()).build();
        }
        log.error("LiveKit webhook processing failed, exceptionType={}", exception.getClass().getName());
        return ResponseEntity.internalServerError().build();
    }
}
