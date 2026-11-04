CREATE TABLE inspection_result_photos (
    photo_id BIGINT NOT NULL AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (photo_id),
    CONSTRAINT uk_inspection_result_photos_object_key UNIQUE (object_key),
    CONSTRAINT fk_inspection_result_photos_result FOREIGN KEY (result_id)
        REFERENCES inspection_results(result_id),
    INDEX idx_inspection_result_photos_result (result_id)
);
