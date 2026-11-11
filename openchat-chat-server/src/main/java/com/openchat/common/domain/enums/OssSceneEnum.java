package com.openchat.common.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 场景枚举
 * Author:
 * Date: 2023-06-20
 */
@AllArgsConstructor
@Getter
public enum OssSceneEnum {
    IMG(4, "图片"),
    VIDEO(5, "视频"),
    FILE(6, "文件"),
    VOICE(7, "音频"),
    SOUND(8, "语音"),
    EMOJI(9, "表情"),
    AVATAR(10, "头像"),
    RELEASE(11, "更新版本路径"),
    FRIEND_TAGS(12, "好友图片标签");

    @EnumValue
    @JsonValue
    private final Integer type;
    private final String desc;

    public String getPath(){
        return this.name().toLowerCase(Locale.ROOT);
    }
}
