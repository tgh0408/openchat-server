package com.openchat.system.domain.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginReq(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 1024) String password) {
    @Override
    public String toString() {
        return "AdminLoginReq[credentials=REDACTED]";
    }
}
