package com.openchat.common.constant;

public interface MQConstant {

    /**
     * 消息发送mq
     */
    String SEND_MSG_TOPIC = "chat_send_msg";
    String SEND_MSG_GROUP = "chat_send_msg_group";

    /**
     * SSE推送
     */
    String SSE_TOPIC = "sse_push";
    String SSE_GROUP = "sse_push_group";
    String RTC_SSE_TOPIC = "rtc_sse_push";
    String RTC_SSE_GROUP = "rtc_sse_push_group";

    /**
     * (授权完成后)登录信息mq
     */
    String LOGIN_MSG_TOPIC = "user_login_send_msg";
    String LOGIN_MSG_GROUP = "user_login_send_msg_group";

    /**
     * 扫码成功 信息发送mq
     */
    String SCAN_MSG_TOPIC = "user_scan_send_msg";
    String SCAN_MSG_GROUP = "user_scan_send_msg_group";



}
