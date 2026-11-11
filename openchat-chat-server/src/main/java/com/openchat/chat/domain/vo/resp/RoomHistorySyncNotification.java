package com.openchat.chat.domain.vo.resp;

import lombok.Getter;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** 本节点热群订阅完成后提示补历史，不表示已读、消息送达或授予房间访问权限。 */
@Getter
public class RoomHistorySyncNotification {
    public static final String EVENT_TYPE = "ROOM_HISTORY_SYNC_REQUIRED";

    private final String eventType = EVENT_TYPE;
    @JsonSerialize(using = ToStringSerializer.class)
    private final Long roomId;

    public RoomHistorySyncNotification(Long roomId) {
        this.roomId = roomId;
    }
}
