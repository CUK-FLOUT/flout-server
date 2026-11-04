CREATE TABLE inspection_applications (
    application_id BIGINT NOT NULL AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    assessment_id BIGINT NOT NULL,
    guide_id BIGINT NULL,
    receipt_no VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255) NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    contact VARCHAR(50) NOT NULL,
    occupancy_type ENUM('OWNER', 'TENANT', 'ETC') NOT NULL,
    flood_experience ENUM('YES', 'NO', 'UNKNOWN') NOT NULL,
    existing_facility BOOLEAN NOT NULL,
    request_reason TEXT NULL,
    preferred_visit_date DATE NULL,
    preferred_time_slot ENUM('MORNING', 'AFTERNOON', 'ANY') NULL,
    internal_memo TEXT NULL,
    status ENUM('SUBMITTED', 'RECEIVED') NOT NULL DEFAULT 'SUBMITTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id),
    CONSTRAINT uk_inspection_applications_receipt_no UNIQUE (receipt_no),
    CONSTRAINT fk_inspection_applications_applicant
        FOREIGN KEY (applicant_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_inspection_applications_assessment
        FOREIGN KEY (assessment_id) REFERENCES risk_assessments(assessment_id),
    CONSTRAINT fk_inspection_applications_guide
        FOREIGN KEY (guide_id) REFERENCES prevention_guides(guide_id),
    INDEX idx_inspection_applications_applicant_created_at (applicant_user_id, created_at),
    INDEX idx_inspection_applications_assessment (assessment_id)
);

CREATE TABLE application_inflow_locations (
    inflow_location_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    inflow_location VARCHAR(20) NOT NULL,
    PRIMARY KEY (inflow_location_id),
    CONSTRAINT fk_application_inflow_locations_application
        FOREIGN KEY (application_id) REFERENCES inspection_applications(application_id),
    CONSTRAINT uk_application_inflow_locations UNIQUE (application_id, inflow_location)
);

CREATE TABLE application_status_histories (
    application_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    status ENUM('SUBMITTED', 'RECEIVED') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (application_status_history_id),
    CONSTRAINT fk_application_status_histories_application
        FOREIGN KEY (application_id) REFERENCES inspection_applications(application_id),
    INDEX idx_application_status_histories_application_created_at (application_id, created_at)
);
