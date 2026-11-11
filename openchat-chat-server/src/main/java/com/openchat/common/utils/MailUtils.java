package com.openchat.common.utils;

import cn.hutool.extra.spring.SpringUtil;
import com.openchat.common.config.MailTemplate;

public class MailUtils {
    private static final MailTemplate mailConfig;

    static {
        mailConfig = SpringUtil.getBean(MailTemplate.class);
    }

    public static void sendMail(String to, String subject, String content) {
        mailConfig.sendMail(to,subject,content);
    }
}
