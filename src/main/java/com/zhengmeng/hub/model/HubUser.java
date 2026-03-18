package com.zhengmeng.hub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hub_user")
public class HubUser {
    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "user_name", nullable = false, length = 128)
    private String userName;

    @Column(length = 256)
    private String department;

    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    @Column(name = "mobile_token", length = 512)
    private String mobileToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
