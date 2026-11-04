CREATE TABLE support_programs (
    program_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    target_description TEXT NULL,
    operating_status VARCHAR(50) NULL COMMENT '목록 표시용 메타데이터. 신청 자격 또는 모집 상태 판정에는 사용하지 않음',
    created_at DATETIME NOT NULL,
    PRIMARY KEY (program_id)
);

CREATE TABLE support_applications (
    support_application_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    requested_facility VARCHAR(255) NOT NULL,
    applicant_name VARCHAR(50) NOT NULL,
    contact VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    occupancy_type ENUM('OWNER','TENANT','ETC') NOT NULL,
    preferred_visit_date DATE NOT NULL COMMENT '지원시설 설치 희망 방문일. 현장점검 및 확정 시공 일정과 별개',
    preferred_visit_time ENUM('MORNING','AFTERNOON','ANY') NOT NULL COMMENT '지원시설 설치 희망 방문 시간대',
    status ENUM('SUBMITTED') NOT NULL DEFAULT 'SUBMITTED',
    submitted_at DATETIME NOT NULL,
    PRIMARY KEY (support_application_id),
    CONSTRAINT uk_support_application_program UNIQUE (application_id, program_id),
    CONSTRAINT fk_support_application_source FOREIGN KEY (application_id) REFERENCES inspection_applications(application_id),
    CONSTRAINT fk_support_application_program FOREIGN KEY (program_id) REFERENCES support_programs(program_id),
    CONSTRAINT fk_support_application_applicant FOREIGN KEY (applicant_user_id) REFERENCES users(user_id),
    INDEX idx_support_applicant_submitted (applicant_user_id, submitted_at, support_application_id)
);

