package com.hive.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hive.delivery")
public record DeliveryProperties(String templatesPath, int maxTicksPerRun) {
    public DeliveryProperties {
        if (templatesPath == null || templatesPath.isBlank()) templatesPath = "../delivery-templates";
        if (maxTicksPerRun <= 0) maxTicksPerRun = 100;
    }
}
