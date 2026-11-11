package com.openchat.common.domain.enums;

import com.openchat.common.exception.BusinessException;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 发送、签名和合并共用容器白名单；文件头不代表完整编码校验。 */
@Getter
public enum VideoFormat {
    MP4("video/mp4"),
    MKV("video/x-matroska"),
    AVI("video/x-msvideo"),
    MOV("video/quicktime"),
    WMV("video/x-ms-wmv"),
    FLV("video/x-flv"),
    WEBM("video/webm"),
    MPEG("video/mpeg");

    private final String contentType;

    VideoFormat(String contentType) {
        this.contentType = contentType;
    }

    public static String extension(String name) {
        if (name == null || name.length() > 255
                || !name.matches("(?i)[^/\\\\\\p{Cntrl}?#]+\\.(mp4|mkv|avi|mov|wmv|flv|webm|mpg|mpeg)")) {
            throw new BusinessException("请选择 MP4、MKV、AVI、MOV、WMV、FLV、WebM 或 MPG/MPEG 视频");
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static VideoFormat fromName(String name) {
        String extension = extension(name);
        return valueOf(("mpg".equals(extension) ? "mpeg" : extension).toUpperCase(Locale.ROOT));
    }

    public boolean matches(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return false;
        boolean iso = text(bytes, 4, "ftyp");
        return switch (this) {
            case MP4 -> iso;
            case MOV -> iso || text(bytes, 4, "moov") || text(bytes, 4, "mdat")
                    || text(bytes, 4, "wide") || text(bytes, 4, "free");
            case MKV, WEBM -> prefix(bytes, 0, 0x1a, 0x45, 0xdf, 0xa3);
            case AVI -> text(bytes, 0, "RIFF") && text(bytes, 8, "AVI ");
            case WMV -> prefix(bytes, 0, 0x30, 0x26, 0xb2, 0x75, 0x8e, 0x66, 0xcf, 0x11,
                    0xa6, 0xd9, 0, 0xaa, 0, 0x62, 0xce, 0x6c);
            case FLV -> prefix(bytes, 0, 70, 76, 86, 1) && (bytes[4] & 1) == 1;
            case MPEG -> prefix(bytes, 0, 0, 0, 1, 0xba) || prefix(bytes, 0, 0, 0, 1, 0xb3);
        };
    }

    private static boolean text(byte[] bytes, int offset, String expected) {
        return bytes.length >= offset + expected.length()
                && expected.equals(new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII));
    }

    private static boolean prefix(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xff) != expected[i]) return false;
        }
        return true;
    }
}
