ALTER TABLE application_attachments
    ADD COLUMN upload_request_key VARCHAR(128) NULL;

ALTER TABLE application_attachments
    ADD COLUMN upload_request_hash VARCHAR(64) NULL;

CREATE INDEX idx_attachment_request_key
    ON application_attachments (application_id, upload_request_key);
