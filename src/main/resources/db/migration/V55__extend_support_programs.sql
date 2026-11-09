-- 지원사업 상세정보 및 자격 검증용 컬럼 확장
ALTER TABLE support_programs
    ADD COLUMN eligibility_conditions TEXT NULL,
    ADD COLUMN exclusion_conditions TEXT NULL,
    ADD COLUMN supported_facilities TEXT NULL,
    ADD COLUMN required_documents TEXT NULL,
    ADD COLUMN application_method TEXT NULL,
    ADD COLUMN application_start_date DATE NULL,
    ADD COLUMN application_end_date DATE NULL,
    ADD COLUMN application_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN official_source VARCHAR(255) NULL,
    ADD COLUMN official_url VARCHAR(2048) NULL;
