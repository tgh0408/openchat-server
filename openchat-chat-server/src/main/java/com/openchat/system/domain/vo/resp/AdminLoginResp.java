package com.openchat.system.domain.vo.resp;

import com.openchat.system.domain.dto.AdminPrincipal;
import org.jspecify.annotations.NonNull;

public record AdminLoginResp(String token, String tokenType, AdminPrincipal admin) {
    @Override
    public @NonNull String toString() {
        return "AdminLoginResp[token=REDACTED]";
    }
}
