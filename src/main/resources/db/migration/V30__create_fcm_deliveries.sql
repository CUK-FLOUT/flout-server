CREATE TABLE fcm_deliveries (
    delivery_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    token_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id),
    CONSTRAINT uk_fcm_deliveries_event_token UNIQUE (event_id, token_id),
    INDEX idx_fcm_deliveries_ready (status, next_attempt_at, delivery_id)
);
