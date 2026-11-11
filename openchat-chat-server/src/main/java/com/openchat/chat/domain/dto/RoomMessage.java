package com.openchat.chat.domain.dto;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomMessage {
    private Long roomId;
    private RoomTypeEnum roomType;
    private Long userId ;
    private Long friendId ;
}
