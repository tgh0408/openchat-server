package com.openchat.common.utils;

import cn.hutool.core.util.RandomUtil;
import com.openchat.common.domain.vo.resp.CheckCodeResp;
import com.wf.captcha.ArithmeticCaptcha;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CaptchaUtils {

    private static final String pre = "Captcha:";

    public static CheckCodeResp captcha() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(200, 80);
        Map<String, String> captchaMap = new HashMap<>();
        String uuid = pre + UUID.randomUUID() + ":" + RandomUtil.randomInt(Integer.MAX_VALUE);

        RedisUtils.set(uuid, captcha.text(), 5, TimeUnit.MINUTES);
        return CheckCodeResp.builder()
                .codeKey(uuid)
                .codeValue(captcha.toBase64())
                .build();
    }
}
