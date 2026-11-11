package com.openchat.chat.domain.vo.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 群主或管理员移出群成员，不接受客户端指定操作者或成员角色。 */
@Data
public class RemoveGroupMembersReq {
    @NotNull
    @Positive
    private Long roomId;

    @NotNull
    @Size(min = 1, max = 100)
    private List<@NotNull @Positive Long> uids;
}
