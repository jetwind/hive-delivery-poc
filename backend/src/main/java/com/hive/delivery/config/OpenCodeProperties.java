package com.hive.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hive.opencode")
public record OpenCodeProperties(boolean enabled, boolean mock, String baseUrl, String username,
                                 String password, String agent, int reconcileDelayMs) {
    public OpenCodeProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://127.0.0.1:4096";
        if (username == null || username.isBlank()) username = "opencode";
        if (agent == null || agent.isBlank()) agent = "build";
        if (reconcileDelayMs <= 0) reconcileDelayMs = 5000;
    }
}
