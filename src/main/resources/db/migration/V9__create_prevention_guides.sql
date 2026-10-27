CREATE TABLE prevention_guides (
    guide_id BIGINT NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    generated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_resolved BOOLEAN NULL,
    unresolved_detail TEXT NULL,
    PRIMARY KEY (guide_id),
    CONSTRAINT uk_prevention_guides_assessment UNIQUE (assessment_id),
    CONSTRAINT fk_prevention_guides_assessment
        FOREIGN KEY (assessment_id) REFERENCES risk_assessments(assessment_id)
);
