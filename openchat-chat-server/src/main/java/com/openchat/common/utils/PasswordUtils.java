package com.openchat.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** 密码只保存随机盐与摘要，服务端密钥不写入数据库。 */
public final class PasswordUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern ENCODED = Pattern.compile("sha256\\$[0-9a-f]{32}\\$[0-9a-f]{64}");

    private PasswordUtils() {
    }

    /** 每次注册或改密生成独立的 128 位随机盐。 */
    public static String encode(String password, String passwordKey) {
        Objects.requireNonNull(password, "密码不能为空");
        requireKey(passwordKey);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "sha256$" + HEX.formatHex(salt) + "$" + HEX.formatHex(digest(password, passwordKey, salt));
    }

    /** 只接受完整的新格式，拒绝明文回退，按字节恒定时间比较摘要。 */
    public static boolean matches(String password, String encoded, String passwordKey) {
        requireKey(passwordKey);
        if (password == null || encoded == null || encoded.length() != 104
                || !ENCODED.matcher(encoded).matches()) {
            return false;
        }
        byte[] salt = HEX.parseHex(encoded.substring(7, 39));
        byte[] expected = HEX.parseHex(encoded.substring(40));
        return MessageDigest.isEqual(expected, digest(password, passwordKey, salt));
    }

    /** 固定顺序为 salt 原始字节、UTF-8 密钥、零字节、UTF-8 密码。 */
    private static byte[] digest(String password, String passwordKey, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(passwordKey.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private static void requireKey(String passwordKey) {
        if (passwordKey == null || passwordKey.isBlank()) {
            throw new IllegalStateException("未配置 openchat.auth.password-key");
        }
    }
}
