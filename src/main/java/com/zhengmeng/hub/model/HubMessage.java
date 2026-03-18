package com.zhengmeng.hub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hub_message")
public class HubMessage {
    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(nullable = false, length = 16)
    private String channel = "work";

    @Column(nullable = false, length = 16)
    private String role;

    @Lob
    private String content;

    @Lob
    @Column(name = "widget_json")
    private String widgetJson;

    @Column(nullable = false, length = 16)
    private String status = "delivered";

    @Column(name = "streaming_phase", length = 32)
    private String streamingPhase;

    @Lob
    @Column(name = "streaming_content")
    private String streamingContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
