package com.openchat.chat.domain.vo.req.msg;

import com.openchat.chat.domain.entity.msg.RtcCallMsgDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RTC 摘要是服务端生成消息，不开放客户端发送。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RtcCallMsgReq {
    private String content;
    private RtcCallMsgDTO rtcCall;
}
