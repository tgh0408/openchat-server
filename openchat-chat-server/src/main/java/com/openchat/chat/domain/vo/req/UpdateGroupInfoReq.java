package com.openchat.chat.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UpdateGroupInfoReq {
    @NotNull
    @Schema(description = "Group room ID")
    private Long roomId;

    @Length(max = 256)
    @Schema(description = "Group name")
    private String groupName;

    @Length(max = 300)
    @Schema(description = "Group announcement")
    private String groupDescription;

    /**
     * 判断本次请求是否至少包含一个需要更新的字段。
     */
    public boolean hasUpdates() {
        return hasGroupName() || hasGroupDescription();
    }

    /**
     * 判断群名称是否为有效更新值。
     */
    public boolean hasGroupName() {
        return hasText(groupName);
    }

    /**
     * 判断群描述是否为有效更新值。
     */
    public boolean hasGroupDescription() {
        return hasText(groupDescription);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
