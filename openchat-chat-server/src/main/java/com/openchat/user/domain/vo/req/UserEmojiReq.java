package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;


/**
 * Description: 表情包反参
 * Author: 
 * Date: 2023-07-09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEmojiReq {

    /**
     * 保存的好友的表情包则携带msgId
     */
    @Schema(description = "消息ID")
    private Long msgId;

    /**
     * 如果本身就为表情包则直接带上表情包ID
     */

    @Schema(description = "表情包ID")
    private Long emojiId;
    /**
     * 房间Id
     */
    @NotNull(message = "消息id不能为null")
    @Schema(description = "消息id")
    private Long roomId;

}
