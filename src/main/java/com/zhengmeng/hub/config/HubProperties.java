package com.zhengmeng.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hub 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "hub")
public class HubProperties {

    private Jwt jwt = new Jwt();
    private FileConfig file = new FileConfig();
    private Device device = new Device();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private int expirationHours = 24;
    }

    @Data
    public static class FileConfig {
        private String storageDir = "/tmp/maodouhub-files";
        private int maxSizeMb = 50;
        private int retentionDays = 90;
    }

    @Data
    public static class Device {
        private int heartbeatTimeoutSeconds = 90;
        private int signatureTimeWindowSeconds = 300;
    }

    @Data
    public static class RateLimit {
        private int messagePerMinute = 10;
        private int filePerHour = 20;
        private int pollIntervalSeconds = 3;
    }
}
