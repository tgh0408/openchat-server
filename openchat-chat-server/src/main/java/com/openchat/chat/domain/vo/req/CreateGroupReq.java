package com.openchat.chat.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建群聊只接受受邀用户，群主及成员角色由服务端确定。 */
@Data
public class CreateGroupReq {
    @NotNull
    @Size(min = 1, max = 100)
    @Schema(description = "受邀好友 UID 列表，至少一位，不包含本人")
    private List<@NotNull @Positive Long> uids;
}
