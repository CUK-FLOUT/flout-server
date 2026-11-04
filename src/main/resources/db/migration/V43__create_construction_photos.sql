CREATE TABLE construction_photos (
    photo_id BIGINT NOT NULL AUTO_INCREMENT,
    construction_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (photo_id),
    CONSTRAINT uk_construction_photo_object_key UNIQUE (object_key),
    CONSTRAINT fk_construction_photo_construction FOREIGN KEY (construction_id)
        REFERENCES constructions(construction_id) ON DELETE CASCADE,
    INDEX idx_construction_photos_construction (construction_id, photo_id)
);
