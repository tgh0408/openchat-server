package com.openchat.system.exception;

import lombok.Getter;

@Getter
public class AdminApiException extends RuntimeException {
    private final int status;

    public AdminApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
