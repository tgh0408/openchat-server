package com.openchat.user.domain.vo.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResp {
    private Long uid;
    private String token;
    private String clientSqlPassword;
}
