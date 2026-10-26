CREATE TABLE risk_assessments (
    assessment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255) NULL,
    risk_level ENUM('HIGH', 'CAUTION', 'LOW') NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    result_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assessment_id),
    CONSTRAINT fk_risk_assessments_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_risk_assessments_user_created_at (user_id, created_at)
);
