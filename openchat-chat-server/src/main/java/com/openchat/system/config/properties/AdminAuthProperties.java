package com.openchat.system.config.properties;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "openchat.dashboard")
public class AdminAuthProperties {

    private boolean enable = true;

    private String username;

    @ToString.Exclude
    private String password;

    private long tokenTtlSeconds = 3600;

    private java.util.List<String> allowedOrigins = java.util.List.of();

}
