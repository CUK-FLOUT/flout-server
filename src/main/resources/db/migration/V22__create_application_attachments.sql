CREATE TABLE application_attachments (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT uk_application_attachments_object_key UNIQUE (object_key),
    CONSTRAINT fk_application_attachments_application FOREIGN KEY (application_id)
        REFERENCES inspection_applications(application_id),
    INDEX idx_application_attachments_application (application_id)
);
