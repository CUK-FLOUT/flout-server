CREATE TABLE construction_photo_uploads (
    upload_id BIGINT NOT NULL AUTO_INCREMENT,
    construction_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_token VARCHAR(36) NOT NULL,
    lease_until DATETIME(6) NOT NULL,
    PRIMARY KEY (upload_id),
    CONSTRAINT uk_construction_photo_upload_scope UNIQUE (construction_id, operator_id, request_key),
    CONSTRAINT fk_photo_upload_construction FOREIGN KEY (construction_id)
        REFERENCES constructions(construction_id) ON DELETE CASCADE
);

ALTER TABLE construction_photos ADD COLUMN upload_id BIGINT NULL;
ALTER TABLE construction_photos ADD CONSTRAINT fk_construction_photo_upload
    FOREIGN KEY (upload_id) REFERENCES construction_photo_uploads(upload_id);
