package com.openchat.system.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.openchat.system.config.properties.AdminAuthProperties;
import com.openchat.system.domain.dto.AdminPrincipal;
import com.openchat.system.domain.vo.resp.AdminLoginResp;
import com.openchat.system.exception.AdminApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 配置账号认证与无状态管理员 JWT；不读写用户表或 Redis 会话。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final String ISSUER = "openchat-dashboard";
    private static final String AUDIENCE = "openchat-admin-api";
    private final AdminAuthProperties properties;

    public AdminLoginResp login(String username, String password) {
        checkConfiguration();
        // 两项均执行常量时间比较，不通过不同提示泄露账号是否存在。
        boolean validUsername = matches(properties.getUsername(), username);
        boolean validPassword = matches(properties.getPassword(), password);
        if (!validUsername || !validPassword) {
            throw new AdminApiException(401, "管理员账号或密码错误");
        }
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(properties.getTokenTtlSeconds());
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(properties.getUsername())
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(Algorithm.HMAC256(properties.getUsername() + properties.getPassword()));
        return new AdminLoginResp(token, "Bearer", principal(expiresAt));
    }

    public AdminPrincipal authenticate(String token) {
        checkConfiguration();
        if (!StringUtils.hasText(token) || token.length() > 8192) {
            throw unauthorized();
        }
        try {
            var jwt = JWT.require(Algorithm.HMAC256(properties.getUsername() + properties.getPassword()))
                    .withIssuer(ISSUER)
                    .withAudience(AUDIENCE)
                    .withSubject(properties.getUsername())
                    .build().verify(token);
            Instant expiresAt = jwt.getExpiresAtAsInstant();
            Instant issuedAt = jwt.getIssuedAtAsInstant();
            if (expiresAt == null || issuedAt == null
                    || !expiresAt.isAfter(issuedAt)
                    || expiresAt.isAfter(issuedAt.plusSeconds(properties.getTokenTtlSeconds()))) {
                throw unauthorized();
            }
            return principal(expiresAt);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private AdminPrincipal principal(Instant expiresAt) {
        return new AdminPrincipal(properties.getUsername(), expiresAt.toString());
    }

    private void checkConfiguration() {
        if (!properties.isEnable()) {
            throw new AdminApiException(503, "管理后台已关闭");
        }
        if (!StringUtils.hasText(properties.getUsername()) || !StringUtils.hasText(properties.getPassword())
                || properties.getUsername().length() > 128 || properties.getPassword().length() > 1024
                || properties.getTokenTtlSeconds() < 300 || properties.getTokenTtlSeconds() > 86400) {
            // 未配置时仅关闭管理接口，不中断聊天服务，也不启用默认账号或密钥。
            throw new AdminApiException(503, "管理后台认证配置不完整或无效");
        }
    }

    private boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private AdminApiException unauthorized() {
        return new AdminApiException(401, "管理员登录已失效，请重新登录");
    }
}
