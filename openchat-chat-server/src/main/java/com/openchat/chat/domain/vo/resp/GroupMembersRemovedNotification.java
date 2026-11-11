package com.openchat.chat.domain.vo.resp;

import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/** 成员变更提示；客户端重新读取权威资料，不按通知累减人数。 */
@Data
@NoArgsConstructor
public class GroupMembersRemovedNotification {
    public static final String EVENT_TYPE = "GROUP_MEMBERS_REMOVED";

    private String eventType = EVENT_TYPE;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> removedUids;

    public GroupMembersRemovedNotification(Long roomId, List<Long> removedUids) {
        this.roomId = roomId;
        this.removedUids = List.copyOf(removedUids);
    }
}
