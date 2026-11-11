package com.openchat.common.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.openchat.common.properties.AuthProperties;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Description: jwt的token生成与解析
 * Author:
 * Date: 2023-04-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    /**
     * token秘钥，请勿泄露，请勿随便修改
     */
    private final AuthProperties authProperties;

    private static final String UID_CLAIM = "uid";
    private static final String CREATE_TIME = "createTime";
    private static final String DEVICE_ID_CLAIM = "deviceId";
    private static final String TERMINAL_CLAIM = "terminal";

    /** 客户端只传系统标识摘要或浏览器内存随机值，不接收原始硬件信息。 */
    public static boolean isValidDeviceId(String deviceId) {
        return deviceId != null && deviceId.matches("[a-f0-9]{64}");
    }

    /**
     * JWT生成Token.<br/>
     * <p>
     * JWT构成: header, payload, signature
     */
    public String createToken(Long uid, String deviceId, TerminalEnum terminal) {
        if (uid == null || uid <= 0 || !isValidDeviceId(deviceId) || terminal == null) {
            throw new IllegalArgumentException("Invalid login identity");
        }
        // build token
        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withClaim(UID_CLAIM, uid)
                .withClaim(DEVICE_ID_CLAIM, deviceId)
                .withClaim(TERMINAL_CLAIM, terminal.name())
                .withClaim(CREATE_TIME, new Date())
                .sign(Algorithm.HMAC256(authProperties.getSecret()));
    }

    /**
     * 验证签名、请求设备和平台；缺少任一绑定声明的旧令牌不再接受。
     *
     */
    public Map<String, Claim> verifyToken(String token, String deviceId, TerminalEnum terminal) {
        if (StringUtils.isEmpty(token) || !isValidDeviceId(deviceId) || terminal == null) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(authProperties.getSecret()))
                    .withClaim(DEVICE_ID_CLAIM, deviceId)
                    .withClaim(TERMINAL_CLAIM, terminal.name())
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaims();
        } catch (Exception e) {
            log.warn("JWT verification failed, exceptionType={}", e.getClass().getSimpleName());
        }
        return null;
    }


    /**
     * 根据Token获取uid
     *
     * @return uid
     */
    public Long getUidOrNull(String token, String deviceId, TerminalEnum terminal) {
        try {
            return Optional.ofNullable(verifyToken(token, deviceId, terminal))
                    .map(map -> map.get(UID_CLAIM))
                    .map(Claim::asLong)
                    .filter(uid -> uid > 0)
                    .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
