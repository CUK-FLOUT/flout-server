CREATE TABLE attachment_upload_intents (
    object_key VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (object_key),
    INDEX idx_attachment_upload_intents_created_at (created_at)
);
