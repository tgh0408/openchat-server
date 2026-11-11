package com.openchat.common.domain.dto;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Description:
 * Author: 
 * Date: 2023-08-12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgSendMessageDTO implements Serializable {
    private Long msgId;
    private RoomTypeEnum roomType;
    /** 原位替换仅重新推送，不再次更新房间活跃和发送者已读位置。 */
    private boolean update;

    public MsgSendMessageDTO(Long msgId, RoomTypeEnum roomType) {
        this(msgId, roomType, false);
    }
}
