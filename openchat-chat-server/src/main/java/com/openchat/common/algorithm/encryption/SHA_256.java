package com.openchat.common.algorithm.encryption;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA_256 {

    public static String hashFile(InputStream fis) throws NoSuchAlgorithmException, IOException {
        try (fis) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[1024];
            int bytesCount;

            // 读取文件并更新摘要
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            // 获取哈希值的字节数组
            byte[] bytes = digest.digest();

            // 将字节数组转换为十六进制格式的字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        }
    }
}
