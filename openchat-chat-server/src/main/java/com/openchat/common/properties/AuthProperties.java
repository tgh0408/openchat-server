package com.openchat.common.properties;

import com.openchat.user.domain.enums.TerminalEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "openchat.auth")
public class AuthProperties {
    private String secret;

    private Map<TerminalEnum,String> clientId;

    private String [] whitePath;

    /** 仅接受完整匹配的已配置标识，不裁剪、多值合并或从请求推测平台。 */
    public TerminalEnum resolveTerminal(String value) {
        if (value == null || clientId == null) return null;
        for (TerminalEnum terminal : TerminalEnum.values()) {
            if (value.equals(clientId.get(terminal))) return terminal;
        }
        return null;
    }
}
