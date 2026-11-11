package com.openchat.chat.domain.vo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 已有群聊直接添加好友，不接受客户端指定操作者或成员角色。 */
@Data
public class AddGroupMembersReq {
    @NotNull
    @Positive
    @Min(0)
    private Long roomId;

    @NotNull
    @Size(min = 1, max = 100)
    private List<@NotNull @Positive Long> uids;
}
