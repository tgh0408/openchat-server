package com.openchat.common.domain.dto;

import lombok.Data;
import com.openchat.user.domain.enums.TerminalEnum;

@Data
public class RequestInfo {
    private Long uid;
    private String ip;
    private String token;
    private String deviceId;
    private TerminalEnum terminal;
}
