package com.openchat.chat.domain.dto;

import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.common.domain.enums.MsgSourceEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事务消息本地事务落库参数
 *
 * @author TGH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaveMsgArg {
    /**
     * 发送消息请求
     */
    private ChatMessageReq request;
    /**
     * 发送者 uid
     */
    private Long uid;
    /**
     * 消息 id
     */
    private Long msgId;

}
