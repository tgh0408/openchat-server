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
 * Description: 热点枚举
 * Author: 
 * Date: 2023-03-19
 */
@AllArgsConstructor
@Getter
public enum HotFlagEnum {
    NOT(0, "非热点"),
    YES(1, "热点");

    @EnumValue
    @JsonValue
    private final Integer type;
    private final String desc;

    private static final Map<Integer, HotFlagEnum> cache;

    static {
        cache = Arrays.stream(HotFlagEnum.values()).collect(Collectors.toMap(HotFlagEnum::getType, Function.identity()));
    }

    public static HotFlagEnum of(Integer type) {
        return cache.get(type);
    }
}
