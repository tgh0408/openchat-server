package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
@Data
public class UseBadgeReq {
    @Schema(description = "徽章ID")
    @NotNull(message = "徽章ID不能为空")
    private Long itemId;
}
