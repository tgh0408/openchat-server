package com.openchat.common.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RespStatusCode {
    SUCCESS(200),
    NOT_FOUND(404),
    SERVER_ERROR(500);
    private final Integer status;
}
