package com.openchat.chat.domain.dto;


import com.openchat.chat.domain.enums.RoomTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageInfo {
    private Long msgId;
    private RoomTypeEnum roomType;
}
