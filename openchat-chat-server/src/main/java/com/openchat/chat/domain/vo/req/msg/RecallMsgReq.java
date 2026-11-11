package com.openchat.chat.domain.vo.req.msg;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 撤回只接受目标标识，不能由客户端指定发送者或待删除对象。 */
@Data
public class RecallMsgReq {
    @NotNull
    @Positive
    private Long msgId;
}
