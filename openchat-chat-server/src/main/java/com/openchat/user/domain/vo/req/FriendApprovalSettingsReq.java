package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 接收方的私有设置，不覆盖申请人原有设置。 */
@Data
public class FriendApprovalSettingsReq {
    @NotNull
    @Size(max = 64)
    private String remark;

    @NotNull
    @Size(max = 10)
    private List<@NotBlank @Size(max = 32) String> tags;

    @NotNull
    @Pattern(regexp = "all|chat")
    @Schema(description = "预留权限参数，暂不生效或持久化")
    private String permission;

    @NotNull
    @Schema(description = "预留朋友圈参数，暂不生效或持久化")
    private Boolean hideMine;

    @NotNull
    @Schema(description = "预留朋友圈参数，暂不生效或持久化")
    private Boolean hideTheirs;
}
