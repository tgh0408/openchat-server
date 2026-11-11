package com.openchat.chat.domain.vo.req;

import com.openchat.chat.domain.enums.MessageTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageReq {
    @NotNull
    @Positive
    @Schema(description = "真实房间id，文件助手从会话列表获取")
    private Long roomId;

    /**
     * @see MessageTypeEnum
     */
    @NotNull
    @Schema(description = "消息类型")
    private MessageTypeEnum msgType;

    @Positive
    @Schema(description = "引用的服务端消息ID（不是clientMsgId），顶层replyId，可不传或为null")
    private Long replyId;

    /**
     * @see com.openchat.chat.domain.entity.msg
     */
    @NotNull
    @Schema(description = "消息内容")
    private Object body;

    @Schema(description = "前端客户端消息id,用于发送确认")
    @NotBlank(message = "clientMsgId不可为空")
    private String clientMsgId;
}
