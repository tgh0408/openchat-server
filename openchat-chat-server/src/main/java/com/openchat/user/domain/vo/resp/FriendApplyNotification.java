package com.openchat.user.domain.vo.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 未读状态失效通知；客户端重新查询计数，不对重复、乱序推送累加。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendApplyNotification {
    public static final String EVENT_TYPE = "FRIEND_APPLY";

    private String eventType = EVENT_TYPE;
    private Long targetUid;

    public FriendApplyNotification(Long targetUid) {
        this.targetUid = targetUid;
    }
}
