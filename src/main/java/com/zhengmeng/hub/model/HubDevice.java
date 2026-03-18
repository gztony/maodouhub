package com.zhengmeng.hub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hub_device")
public class HubDevice {
    @Id
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "device_name", length = 128)
    private String deviceName;

    @Column(name = "device_secret", nullable = false, length = 256)
    private String deviceSecret;

    @Column(name = "is_online", nullable = false)
    private boolean online = false;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "channel_type", length = 16)
    private String channelType = "none";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
