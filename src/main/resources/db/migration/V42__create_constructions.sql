CREATE TABLE constructions (
    construction_id BIGINT NOT NULL AUTO_INCREMENT,
    support_application_id BIGINT NOT NULL,
    status ENUM('PENDING','SCHEDULED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
    scheduled_at DATETIME NULL,
    manager_user_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (construction_id),
    CONSTRAINT uk_construction_support UNIQUE (support_application_id),
    CONSTRAINT fk_construction_support FOREIGN KEY (support_application_id)
        REFERENCES support_applications(support_application_id) ON DELETE CASCADE,
    CONSTRAINT fk_construction_manager FOREIGN KEY (manager_user_id) REFERENCES users(user_id),
    INDEX idx_constructions_status_schedule (status, scheduled_at)
);

-- 이미 승인된 신청도 일정 저장 대상에 포함한다. 확정 일정은 희망 일정으로 대체하지 않는다.
INSERT INTO constructions (support_application_id, status, created_at, updated_at)
SELECT support_application_id, 'PENDING', COALESCE(approved_at, submitted_at),
       COALESCE(approved_at, submitted_at)
FROM support_applications
WHERE status = 'APPROVED';
