package com.openchat.common.properties;

import com.openchat.user.domain.enums.TerminalEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "openchat.auth")
public class AuthProperties {
    /** 密码摘要的服务端密钥，各节点必须一致；已有账号生成后不能直接更换。 */
    @lombok.ToString.Exclude
    private String passwordKey;

    private String secret;

    private Map<TerminalEnum,String> clientId;

    /** 免 JWT 校验的路径；设备校验由 deviceWhitePath 单独控制。 */
    private String[] whitePath = new String[0];

    /** 免平台和设备标识校验的路径，默认不放行任何路径。 */
    private String[] deviceWhitePath = new String[0];

    /** 仅接受完整匹配的已配置标识，不裁剪、多值合并或从请求推测平台。 */
    public TerminalEnum resolveTerminal(String value) {
        if (value == null || clientId == null) return null;
        for (TerminalEnum terminal : TerminalEnum.values()) {
            if (value.equals(clientId.get(terminal))) return terminal;
        }
        return null;
    }
}
