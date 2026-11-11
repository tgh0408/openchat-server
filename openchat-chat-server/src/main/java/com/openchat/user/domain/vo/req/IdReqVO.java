package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdReqVO {
    @Schema(description = "表情包id")
    @NotNull
    private long imgId;
}
