package com.openchat.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.event.LoginSessionChangedEvent;
import com.openchat.common.utils.JwtUtils;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoginService {
    final JwtUtils jwtUtils;
    private final LoginSessionStore sessions;
    private final MQProducer mqProducer;
    private final ApplicationEventPublisher events;

    private static final long TOKEN_RENEWAL_THRESHOLD_SECONDS = Duration.ofDays(1).toSeconds();
    private static final Cache<String, RenewalSnapshot> tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(6000)
            .recordStats()
            .build();

    //刷新token
    private void renewalTokenIfNecessary(Long uid, String token, TerminalEnum terminal) {
        if (uid == null || StringUtils.isBlank(token) || terminal == null) return;
        long now = System.nanoTime();
        RenewalSnapshot snapshot = tokenCache.getIfPresent(token);
        if (snapshot != null && !snapshot.shouldRenew(now)) return;

        try {
            tokenCache.asMap().compute(token, (ignored, current) -> {
                long currentTime = System.nanoTime();
                if (current != null && !current.shouldRenew(currentTime)) return current;
                Long ttlSeconds = sessions.renew(uid, terminal, token);
                if (ttlSeconds == null) return null;
                return new RenewalSnapshot(ttlSeconds, currentTime);
            });
        } catch (RuntimeException exception) {
            log.warn("Token session renewal failed; request validation will continue, uid={}", uid);
        }
    }

    /** 登录成功后只替换当前平台的设备绑定令牌，其他平台保持在线。 */
    public String login(Long uid, String deviceId, TerminalEnum terminal) {
        String token = jwtUtils.createToken(uid, deviceId, terminal);
        sessions.replace(uid, terminal, token);
        notifySessionChanged(uid);
        return token;
    }

    /** 只撤销已鉴权请求绑定的平台与令牌，重复退出仍触发连接复核。 */
    public void logout(Long uid, TerminalEnum terminal, String token) {
        sessions.revoke(uid, terminal, token);
        tokenCache.invalidate(token);
        notifySessionChanged(uid);
    }

    private void notifySessionChanged(Long uid) {
        // 先撤销旧令牌再通知；通知失败不回滚令牌，SSE 心跳与请求校验继续兜底。
        try {
            events.publishEvent(new LoginSessionChangedEvent(uid));
        } catch (RuntimeException exception) {
            log.warn("Local login session notification failed, uid={}", uid);
        }
        try {
            mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC, PushMessageDTO.sessionChanged(uid));
        } catch (RuntimeException exception) {
            log.warn("Cluster login session notification failed; heartbeat will reconcile, uid={}", uid);
        }
    }

    /** 仅在令牌的平台、设备绑定有效且仍为该平台当前会话时返回 uid。 */
    public Long getValidUid(String token, String deviceId, TerminalEnum terminal) {
        Long uid = jwtUtils.getUidOrNull(token, deviceId, terminal);
        if (Objects.isNull(uid)) return null;
        String old_token = sessions.currentToken(uid, terminal);
        if (StringUtils.isBlank(old_token)) return null;
        if (!Objects.equals(token, old_token)) return null;
        renewalTokenIfNecessary(uid, token, terminal);
        return uid;
    }

    private record RenewalSnapshot(long ttlSeconds, long checkedAtNanos) {
        private boolean shouldRenew(long nowNanos) {
            if (ttlSeconds < 0) return false;
            long elapsedSeconds = Math.max(0L, (nowNanos - checkedAtNanos) / 1_000_000_000L);
            return ttlSeconds - elapsedSeconds < TOKEN_RENEWAL_THRESHOLD_SECONDS;
        }
    }

}
