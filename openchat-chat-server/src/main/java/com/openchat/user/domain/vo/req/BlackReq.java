package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class BlackReq {
    @Schema(description = "用于校验双方身份的单聊房间ID")
    @NotNull
    @Positive
    private Long roomId;

    @Schema(description = "拉黑用户的ID")
    @NotNull
    @Positive
    private Long userId;
}
