-- MaoDouHub 初始化建表
-- V1: 用户、设备、消息、文件、审计、幂等

-- 用户表
CREATE TABLE hub_user (
    user_id        VARCHAR(64)  PRIMARY KEY,
    user_name      VARCHAR(128) NOT NULL,
    department     VARCHAR(256),
    password_hash  VARCHAR(256),
    mobile_token   VARCHAR(512),
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at  TIMESTAMP
);

-- PC 设备表
CREATE TABLE hub_device (
    device_id       VARCHAR(64)  PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    device_name     VARCHAR(128),
    device_secret   VARCHAR(256) NOT NULL,
    is_online       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_heartbeat  TIMESTAMP,
    channel_type    VARCHAR(16)  DEFAULT 'none',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_device_user ON hub_device(user_id);
CREATE INDEX idx_device_online ON hub_device(is_online);

-- 消息表
CREATE TABLE hub_message (
    message_id        VARCHAR(64)  PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    request_id        VARCHAR(64),
    channel           VARCHAR(16)  NOT NULL DEFAULT 'work',
    role              VARCHAR(16)  NOT NULL,
    content           CLOB,
    widget_json       CLOB,
    status            VARCHAR(16)  NOT NULL DEFAULT 'delivered',
    streaming_phase   VARCHAR(32),
    streaming_content CLOB,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_message_user_channel ON hub_message(user_id, channel, created_at);
CREATE INDEX idx_message_request ON hub_message(request_id);

-- 文件表
CREATE TABLE hub_file (
    file_id       VARCHAR(64)  PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    message_id    VARCHAR(64),
    file_name     VARCHAR(256) NOT NULL,
    file_size     BIGINT       NOT NULL,
    content_type  VARCHAR(128),
    storage_path  VARCHAR(512) NOT NULL,
    direction     VARCHAR(8)   NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_file_user ON hub_file(user_id);
CREATE INDEX idx_file_message ON hub_file(message_id);

-- 审计表
CREATE TABLE hub_audit (
    audit_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    detail      CLOB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_user ON hub_audit(user_id, created_at);

-- 幂等表
CREATE TABLE hub_idempotency (
    request_id     VARCHAR(64)  PRIMARY KEY,
    response_json  CLOB,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_idempotency_expire ON hub_idempotency(created_at);
