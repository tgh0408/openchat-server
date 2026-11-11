package com.openchat.chat.domain.vo.req.msg;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmojiMsgReq {

    @Schema(description = "表情包id")
    @NotNull(message = "表情包ID不能为空")
    @Positive(message = "表情包ID必须为正数")
    private Long emojiId;

    @Schema(description = "回复的消息id,如果没有别传就好")
    private Long replyMsgId;

    @Schema(description = "艾特的uid")
    @Size(max = 10, message = "一次最多@10个人")
    private List<Long> atUidList;
}
