package com.openchat.chat.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 消息状态
 * Author: 
 * Date: 2023-03-19
 */
@AllArgsConstructor
@Getter
public enum RoomTypeEnum {
    GROUP(1, "群聊"),
    FRIEND(2, "单聊"),
    FILE_ASSISTANT(3, "文件助手");

    @EnumValue
    @JsonValue
    private final Integer type;
    private final String desc;

    /** 单聊和文件助手共用 message_user，群聊使用 message。 */
    public boolean usesUserMessageTable() {
        return this == FRIEND || this == FILE_ASSISTANT;
    }

}
