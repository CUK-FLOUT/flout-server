CREATE TABLE inspection_results (
    result_id BIGINT NOT NULL AUTO_INCREMENT,
    inspection_id BIGINT NOT NULL,
    summary TEXT NULL,
    follow_up_type ENUM(
    'INDIVIDUAL_HOUSE',
    'PUBLIC_AREA'
    ) NULL,
    internal_memo TEXT NULL,
    result_status ENUM('DRAFT', 'PUBLISHED')
        NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (result_id),
    CONSTRAINT uk_inspection_result_status
        UNIQUE (inspection_id, result_status),
    CONSTRAINT fk_inspection_result_inspection
        FOREIGN KEY (inspection_id)
        REFERENCES inspections(inspection_id)
);

CREATE TABLE inspection_vulnerabilities (
    vulnerability_id BIGINT NOT NULL AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    type ENUM(
        'ENTRANCE',
        'WINDOW_OPENING',
        'DRAIN',
        'OTHER_STRUCTURE'
    ) NULL,
    title VARCHAR(255) NULL,
    expert_opinion TEXT NULL,
    field_findings TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (vulnerability_id),
    CONSTRAINT fk_vulnerability_result
        FOREIGN KEY (result_id)
        REFERENCES inspection_results(result_id)
);

CREATE TABLE prevention_recommendations (
    recommendation_id BIGINT NOT NULL AUTO_INCREMENT,
    result_id BIGINT NOT NULL,
    title VARCHAR(255) NULL,
    related_target ENUM(
        'ENTRANCE',
        'WINDOW_OPENING',
        'DRAIN',
        'OTHER_STRUCTURE'
    ) NULL,
    description TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (recommendation_id),
    CONSTRAINT fk_recommendation_result
        FOREIGN KEY (result_id)
        REFERENCES inspection_results(result_id)
);

ALTER TABLE inspection_applications
MODIFY COLUMN status ENUM(
    'RECEIVED',
    'INSPECTION_COMPLETED'
) NOT NULL DEFAULT 'RECEIVED';

ALTER TABLE application_status_histories
MODIFY COLUMN status ENUM(
    'RECEIVED',
    'INSPECTION_COMPLETED'
) NOT NULL;