package com.openchat.common.service;

import com.openchat.common.domain.vo.resp.CheckCodeResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.CaptchaUtils;
import com.openchat.common.utils.RedisUtils;

public class CaptchaService {

    public static void checkCaptcha(String captchaId,String captcha) {
        String redis_result = RedisUtils.get(captchaId, String.class);
        try {
            if (redis_result == null) {
                throw new BusinessException("验证码信息不存在");
            }
            if(!redis_result.equals(captcha)) {
                throw new BusinessException("验证码验证未通过");
            }
        }finally {
            if(redis_result != null) {
                RedisUtils.del(captchaId, redis_result);
            }
        }
    }

    public static CheckCodeResp getAndSetCaptcha() {
        return CaptchaUtils.captcha();
    }
}
