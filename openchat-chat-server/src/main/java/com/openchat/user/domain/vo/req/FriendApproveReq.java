package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendApproveReq {

    @NotNull
    @Positive
    @Schema(description = "申请id")
    private Long applyId;

    @NotNull
    @Positive
    private Long friendId;

    @Valid
    @Schema(description = "接收方私有设置；旧客户端未传时保持原行为")
    private FriendApprovalSettingsReq settings;

}
