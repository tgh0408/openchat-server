package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description:
 * Author: 
 * Date: 2023-07-29
 */
@AllArgsConstructor
@Getter
public enum SSEPushTypeEnum {
    USER(1, "个人"),
    ALL(2, "全部连接用户"),
    ROOM(3, "房间在线成员");

    @EnumValue
    @JsonValue
    private final Integer type;
    private final String desc;

    private static final Map<Integer, SSEPushTypeEnum> cache;

    static {
        cache = Arrays.stream(SSEPushTypeEnum.values()).collect(Collectors.toMap(SSEPushTypeEnum::getType, Function.identity()));
    }

    public static SSEPushTypeEnum of(Integer type) {
        return cache.get(type);
    }
}
