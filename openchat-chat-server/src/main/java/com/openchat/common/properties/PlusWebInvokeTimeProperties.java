package com.openchat.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openchat.plus-web-invoke-time-interceptor")
public class PlusWebInvokeTimeProperties {

    private boolean enabled;

    private String [] allowedPath = new String[]{};

    private boolean printHeader = false;

    private String [] excludeProperties = { "clientid","password", "oldPassword", "newPassword", "confirmPassword" };

    private String [] responseExcludeProperties = {
            "clientid", "password", "oldPassword", "newPassword", "confirmPassword",
            "token", "accessToken", "refreshToken"
    };
}
