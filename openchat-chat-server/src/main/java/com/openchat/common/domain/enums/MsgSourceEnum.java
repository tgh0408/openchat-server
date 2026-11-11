package com.openchat.common.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openchat.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@AllArgsConstructor
@Getter
public enum MsgSourceEnum {
    CLIENT("CLIENT", "客户端消息"),
    SERVER("SERVER", "服务端消息");

    @EnumValue
    @JsonValue
    private final String source;
    private final String desc;

    private static final Map<String, MsgSourceEnum> cache;
    static {
        cache = Arrays.stream(MsgSourceEnum.values())
                .collect(Collectors.toMap(MsgSourceEnum::getSource, Function.identity()));
    }

    public static MsgSourceEnum of(String source) {
        return Optional.ofNullable(cache.get(source)).orElseThrow(() -> new BusinessException("消息类型转换失败"));
    }
}