package com.openchat.common.event;

import java.util.List;

/** 同一事务内实际移出的成员，不包含已退出或不存在的目标。 */
public record GroupMembersRemovedEvent(Long roomId, List<Long> removedUids) {
    public GroupMembersRemovedEvent {
        removedUids = List.copyOf(removedUids);
    }
}
