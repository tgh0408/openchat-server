package com.openchat.user.domain.vo.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 删除好友时可选择同时清空本人的会话历史。 */
@Data
public class FriendDeleteReq {
    @NotNull
    private Boolean clearHistory = false;
}
