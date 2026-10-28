CREATE TABLE inspections (
    inspection_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    scheduled_at DATETIME NULL,
    status ENUM('WAITING', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'WAITING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (inspection_id),
    CONSTRAINT uk_inspections_application UNIQUE (application_id),
    CONSTRAINT fk_inspections_application FOREIGN KEY (application_id) REFERENCES inspection_applications(application_id),
    INDEX idx_inspections_status_scheduled_at (status, scheduled_at)
);

CREATE TABLE inspection_status_histories (
    inspection_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT NOT NULL,
    previous_status ENUM('WAITING', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL,
    status ENUM('WAITING', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL,
    changed_by_user_id BIGINT NULL,
    change_source ENUM('MANUAL', 'AUTO') NOT NULL,
    changed_at DATETIME NOT NULL,
    PRIMARY KEY (inspection_status_history_id),
    CONSTRAINT fk_inspection_status_histories_inspection FOREIGN KEY (inspection_id) REFERENCES inspections(inspection_id),
    CONSTRAINT fk_inspection_status_histories_user FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id),
    INDEX idx_inspection_status_histories_inspection_changed_at (inspection_id, changed_at)
);

INSERT INTO inspections (application_id, status)
SELECT application_id, 'WAITING' FROM inspection_applications WHERE status = 'RECEIVED';
