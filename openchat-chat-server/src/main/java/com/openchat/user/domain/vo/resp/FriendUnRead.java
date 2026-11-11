package com.openchat.user.domain.vo.resp;

import com.openchat.user.domain.enums.ApplyStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前账号尚未查看的好友申请摘要。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendUnRead {
    /** 当前阅读者的对方 UID，包含收到的申请和发出申请的新回复。 */
    private Long uid;
    private ApplyStatusEnum applyStatus;
}
