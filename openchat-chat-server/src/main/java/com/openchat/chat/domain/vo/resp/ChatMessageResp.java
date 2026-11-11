package com.openchat.chat.domain.vo.resp;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.Date;

/**
 * Description: 消息
 * Author:
 * Date: 2023-03-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageResp {

    @Schema(description = "发送者信息")
    private UserInfo fromUser;
    @Schema(description = "消息详情")
    private Message message;

    @Data
    public static class UserInfo {
        @Schema(description = "用户id")
        private Long uid;
    }

    @Data
    public static class Message {
        @Schema(description = "消息id")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @Schema(description = "房间id")
        private Long roomId;
        @Schema(description = "消息发送时间")
        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        private Date sendTime;
        @Schema(description = "消息类型 1正常文本 2.撤回消息 3.大文件内容占位 4.图片 5.视频 6.文件 7.音频 8.语音 9.表情 10 系统消息 11RTC消息")
        private MessageTypeEnum type;
        @Schema(description = "消息内容不同的消息类型，内容体不同")
        private Object body;
        @Schema(description = "前端客户端消息id,用于发送确认")
        private String clientMsgId;
        @Schema(description = "引用的服务端消息ID，无引用时为null")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long replyId;
        @Schema(description = "落库时两条消息之间的正常状态消息数，不含两端；仅作提示，无引用时为null")
        private Integer gapCount;
    }

}
