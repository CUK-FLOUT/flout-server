CREATE TABLE public_issues (
    public_issue_id BIGINT NOT NULL AUTO_INCREMENT,
    source_inspection_id BIGINT NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    issue_type ENUM('REPEATED_FLOODING', 'SEWAGE_BACKFLOW', 'POOR_ROAD_DRAINAGE', 'BLOCKED_STORM_DRAIN') NOT NULL,
    description TEXT NOT NULL,
    field_findings TEXT NOT NULL,
    status ENUM('RECEIVED') NOT NULL DEFAULT 'RECEIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (public_issue_id),
    CONSTRAINT fk_public_issues_inspection FOREIGN KEY (source_inspection_id)
        REFERENCES inspections(inspection_id),
    INDEX idx_public_issues_source (source_inspection_id),
    INDEX idx_public_issues_created (created_at, public_issue_id)
);
