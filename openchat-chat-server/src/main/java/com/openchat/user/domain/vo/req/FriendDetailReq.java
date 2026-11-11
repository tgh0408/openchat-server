package com.openchat.user.domain.vo.req;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 查询当前登录用户的好友资料，不接受调用方指定关系所有者。 */
@Data
public class FriendDetailReq {
    @NotNull(message = "非法的用户id")
    @Positive(message = "非法的用户id")
    private Long targetUid;

    /** 必须绑定来源单聊或群聊房间，由服务端核对类型及访问权限。 */
    @Positive(message = "非法的房间id")
    @NotNull(message = "非法的房间id")
    private Long roomId;
}
