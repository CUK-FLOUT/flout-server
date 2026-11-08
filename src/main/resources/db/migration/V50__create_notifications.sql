CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    application_id BIGINT NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    CONSTRAINT uk_notifications_event UNIQUE (event_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_notifications_user_created (user_id, created_at, notification_id)
);
