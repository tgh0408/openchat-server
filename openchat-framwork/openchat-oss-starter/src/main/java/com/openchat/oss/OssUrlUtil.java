package com.openchat.oss;

import java.net.URI;

/** OSS 对象地址转换工具，不改变数据库中的对象路径。 */
public final class OssUrlUtil {
    private OssUrlUtil() {
    }

    public static String toPublicUrl(String endpoint, String path) {
        return toPublicUrl(endpoint, null, path);
    }

    public static String toPublicUrl(String endpoint, String bucket, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("OSS 对象地址无效");
        }
        try {
            URI source = URI.create(path);
            String result;
            if (source.isAbsolute()) {
                result = path;
            } else {
                if (endpoint == null || endpoint.isBlank() || path.startsWith("//")) {
                    throw new IllegalArgumentException();
                }
                String normalizedPath = path.replaceAll("^/+", "");
                if (bucket != null && !bucket.isBlank()
                        && !normalizedPath.equals(bucket)
                        && !normalizedPath.startsWith(bucket + "/")) {
                    normalizedPath = bucket + "/" + normalizedPath;
                }
                result = endpoint.replaceAll("/+$", "") + "/" + normalizedPath;
            }
            URI url = URI.create(result);
            if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))
                    || url.getHost() == null || url.getUserInfo() != null || result.length() > 2048) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("OSS 对象地址无效", exception);
        }
    }
}
