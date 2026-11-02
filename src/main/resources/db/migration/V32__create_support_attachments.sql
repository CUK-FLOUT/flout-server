CREATE TABLE support_attachments (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    support_application_id BIGINT NOT NULL,
    attachment_type ENUM('LANDLORD_CONSENT','OWNERSHIP_PROOF') NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    submitted_at DATETIME NOT NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT uk_support_attachment_type UNIQUE (support_application_id, attachment_type),
    CONSTRAINT uk_support_attachment_object_key UNIQUE (object_key),
    CONSTRAINT fk_support_attachment_application FOREIGN KEY (support_application_id)
        REFERENCES support_applications(support_application_id)
);

