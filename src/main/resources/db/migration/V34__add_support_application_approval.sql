ALTER TABLE support_applications
    MODIFY COLUMN status ENUM('SUBMITTED','APPROVED') NOT NULL DEFAULT 'SUBMITTED';
ALTER TABLE support_applications ADD COLUMN support_scope VARCHAR(500) NULL;
ALTER TABLE support_applications ADD COLUMN approved_by_user_id BIGINT NULL;
ALTER TABLE support_applications ADD COLUMN approved_at DATETIME NULL;
ALTER TABLE support_applications
    ADD COLUMN landlord_consent_status ENUM('NOT_REQUIRED','PENDING','CONFIRMED') NOT NULL DEFAULT 'PENDING';
ALTER TABLE support_applications
    ADD CONSTRAINT fk_support_approval_operator FOREIGN KEY (approved_by_user_id) REFERENCES users(user_id);

UPDATE support_applications SET landlord_consent_status = 'NOT_REQUIRED' WHERE occupancy_type = 'OWNER';

CREATE TABLE support_application_status_histories (
    support_application_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    support_application_id BIGINT NOT NULL,
    previous_status ENUM('SUBMITTED','APPROVED') NOT NULL,
    status ENUM('SUBMITTED','APPROVED') NOT NULL,
    support_scope VARCHAR(500) NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_at DATETIME NOT NULL,
    PRIMARY KEY (support_application_status_history_id),
    CONSTRAINT fk_support_history_application FOREIGN KEY (support_application_id) REFERENCES support_applications(support_application_id),
    CONSTRAINT fk_support_history_operator FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id),
    INDEX idx_support_history_application_time (support_application_id, changed_at)
);
