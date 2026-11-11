package com.openchat.user.domain.vo.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BadgeResp {
    @Schema(description = "徽章ID")
    private Long id;
    @Schema(description = "徽章图标")
    private String img;
    @Schema(description = "徽章描述")
    private String describe;
    @Schema(description = "是否拥有徽章 0否 1是")
    private Integer obtain;
    @Schema(description = "是否佩戴 0否 1是")
    private Integer isUsed;

}
