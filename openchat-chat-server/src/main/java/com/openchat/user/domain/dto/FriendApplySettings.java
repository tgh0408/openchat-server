package com.openchat.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 申请人的私有设置快照；朋友圈字段仅保留，不作为授权依据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendApplySettings {
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "标签")
    private List<String> tags;
    @Schema(description = "权限")
    private String permission;
    @Schema(description = "不让他看")
    private Boolean hideMine;
    @Schema(description = "不看他")
    private Boolean hideTheirs;
}
