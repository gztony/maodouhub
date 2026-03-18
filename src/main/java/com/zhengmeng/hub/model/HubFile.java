package com.zhengmeng.hub.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "hub_file")
public class HubFile {
    @Id
    @Column(name = "file_id", length = 64)
    private String fileId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(nullable = false, length = 8)
    private String direction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
