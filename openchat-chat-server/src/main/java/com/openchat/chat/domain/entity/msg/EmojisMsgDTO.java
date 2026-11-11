package com.openchat.chat.domain.entity.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * Description: 表情图片消息入参
 * Author: 
 * Date: 2023-06-04
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmojisMsgDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "表情包ID")
    @NotNull
    private Long emojiId;

    @Schema(description = "表情包描述")
    private String description;
}


