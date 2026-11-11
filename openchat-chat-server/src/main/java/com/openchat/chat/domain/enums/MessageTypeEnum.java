package com.openchat.chat.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
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
public enum MessageTypeEnum {
    TEXT(1, "正常消息"),
    RECALL(2, "撤回消息"),
    TEMP(3, "大文件内容占位"),
    IMG(4,"图片"),
    VIDEO(5,"视频"),
    FILE(6, "文件"),
    VOICE(7, "音频"),
    SOUND(8,"语音"),
    EMOJI(9, "表情"),
    SYSTEM(10, "系统消息"),
    RTC_CALL(11, "RTC通话")
    ;

    @EnumValue
    @JsonValue
    private final Integer type;
    private final String desc;

    /**可被引用的消息类型**/
    public static final List<MessageTypeEnum> REPLAYABLE_TYPES = List.of(TEXT, IMG, VIDEO, FILE, VOICE, SOUND, EMOJI);

    public static boolean equalsIn(MessageTypeEnum type , MessageTypeEnum ...messageTypeEnum) {
        if(type == null){
            return false;
        }
        for (MessageTypeEnum e : messageTypeEnum) {
            if(e.equals(type)){
                return true;
            }
        }
        return false;
    }
}
