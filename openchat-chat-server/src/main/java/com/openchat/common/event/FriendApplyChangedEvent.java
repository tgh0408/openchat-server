package com.openchat.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** 申请已读或处理后的账号状态变化，与原业务事务一起保存通知。 */
@Getter
public class FriendApplyChangedEvent extends ApplicationEvent {
    private final Long uid;

    public FriendApplyChangedEvent(Object source, Long uid) {
        super(source);
        this.uid = uid;
    }
}
