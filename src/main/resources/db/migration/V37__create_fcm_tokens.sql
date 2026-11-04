CREATE TABLE fcm_tokens (
    token_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(2048) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token_id),
    CONSTRAINT uk_fcm_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_fcm_tokens_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_fcm_tokens_user (user_id)
);
