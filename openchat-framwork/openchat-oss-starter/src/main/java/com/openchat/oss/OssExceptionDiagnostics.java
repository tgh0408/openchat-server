package com.openchat.oss;

import io.minio.errors.ErrorResponseException;
import okhttp3.Response;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/** 仅为日志生成脱敏异常副本，不改变用于业务判断和重试的原始异常。 */
public final class OssExceptionDiagnostics {
    private static final int MAX_EXCEPTIONS = 32;
    private static final int MAX_FRAMES = 128;
    private static final int MAX_MESSAGE = 2048;
    private static final String SECRET_NAMES = "authorization|proxy-authorization|cookie|set-cookie"
            + "|access[-_]?key(?:[-_]?id)?|secret(?:[-_]?(?:access[-_]?)?key)?"
            + "|password|passwd|pwd|token|jwt|signature|credential|client[-_]?secret"
            + "|x-amz-(?:signature|credential|security-token)";
    private static final Pattern URL = Pattern.compile(
            "(?i)\\b[a-z][a-z0-9+.-]*(?::[a-z][a-z0-9+.-]*)?://[^\\s<>\"']+");
    private static final Pattern XML_SECRET = Pattern.compile(
            "(?is)<(" + SECRET_NAMES + ")\\s*>.*?</\\1\\s*>");
    private static final Pattern SECRET_FIELD = Pattern.compile(
            "(?i)(\\b(?:(?:aws|oss|s3|minio)[._-])?(?:" + SECRET_NAMES
                    + ")[\"']?\\s*[:=]\\s*)[^\\r\\n]*");
    private static final Pattern AUTH_VALUE = Pattern.compile(
            "(?i)\\b(?:Bearer|Basic)\\s+[a-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    private OssExceptionDiagnostics() {
    }

    /** 保留异常类型、原因链、清理异常和原堆栈，避免调用 SDK 的原始 toString/httpTrace。 */
    public static Throwable sanitized(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return copy(error, visited);
    }

    private static Throwable copy(Throwable error, Set<Throwable> visited) {
        if (visited.size() >= MAX_EXCEPTIONS || !visited.add(error)) {
            RuntimeException truncated = new RuntimeException("[exception chain truncated or circular]");
            truncated.setStackTrace(new StackTraceElement[0]);
            return truncated;
        }
        String detail = error.getClass().getName() + ": " + sanitizeMessage(error.getMessage());
        if (error instanceof ErrorResponseException storageError) {
            if (storageError.errorResponse() != null) {
                detail += " [s3Code=" + identifier(storageError.errorResponse().code())
                        + ", requestId=" + identifier(storageError.errorResponse().requestId()) + "]";
            }
            // 响应由 SDK/调用方持有；诊断只读状态码，不接管或关闭原始响应。
            Response response = storageError.response();
            if (response != null) {
                detail += " [httpStatus=" + response.code() + "]";
            }
        }
        RuntimeException safe = new RuntimeException(detail);
        StackTraceElement[] frames = error.getStackTrace();
        safe.setStackTrace(Arrays.copyOf(frames, Math.min(frames.length, MAX_FRAMES)));
        if (error.getCause() != null) {
            safe.initCause(copy(error.getCause(), visited));
        }
        for (Throwable suppressed : error.getSuppressed()) {
            safe.addSuppressed(copy(suppressed, visited));
            if (visited.size() >= MAX_EXCEPTIONS) break;
        }
        return safe;
    }

    private static String sanitizeMessage(String message) {
        if (message == null) return "";
        if (message.length() > 16384) return "[oversized message omitted]";
        String safe = URL.matcher(message).replaceAll("[URL REDACTED]");
        safe = XML_SECRET.matcher(safe).replaceAll("<$1>[REDACTED]</$1>");
        // 凭据字段之后可能跟随空格或复合签名，整行剩余内容一起隐藏。
        safe = SECRET_FIELD.matcher(safe).replaceAll("$1[REDACTED]");
        safe = AUTH_VALUE.matcher(safe).replaceAll("[AUTH REDACTED]");
        safe = JWT.matcher(safe).replaceAll("[JWT REDACTED]");
        safe = CONTROL.matcher(safe).replaceAll(" ");
        if (safe.length() > MAX_MESSAGE) return safe.substring(0, MAX_MESSAGE) + " [truncated]";
        return safe;
    }

    private static String identifier(String value) {
        if (value == null) return "";
        if (!value.matches("[A-Za-z0-9._:/+-]{1,128}")) return "[REDACTED]";
        return value;
    }
}
