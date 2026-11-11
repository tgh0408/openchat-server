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
 * 会话显示状态。
 */
@AllArgsConstructor
@Getter
public enum ContactStateEnum {
    NORMAL(0, "正常"),
    DELETED(1, "删除"),
    HIDDEN(2, "隐藏");

    @EnumValue
    @JsonValue
    private final Integer value;
    private final String desc;

    private static final Map<Integer, ContactStateEnum> CACHE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ContactStateEnum::getValue, Function.identity()));

    /**
     * 按数据库状态值转换枚举。
     */
    public static ContactStateEnum of(Integer value) {
        return CACHE.get(value);
    }
}
