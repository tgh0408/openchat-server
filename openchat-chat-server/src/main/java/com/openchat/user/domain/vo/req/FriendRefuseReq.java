package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendRefuseReq {

    @NotNull
    @Schema(description = "申请id")
    private Long refuseId;

    @NotNull
    @Schema(description = "applyId")
    private Long applyId;

}
