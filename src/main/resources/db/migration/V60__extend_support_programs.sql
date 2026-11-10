
-- 지원사업 상세정보 및 자격 검증용 컬럼 확장

ALTER TABLE support_programs
    ADD COLUMN eligibility_conditions TEXT NULL;

ALTER TABLE support_programs
    ADD COLUMN exclusion_conditions TEXT NULL;

ALTER TABLE support_programs
    ADD COLUMN supported_facilities TEXT NULL;

ALTER TABLE support_programs
    ADD COLUMN required_documents TEXT NULL;

ALTER TABLE support_programs
    ADD COLUMN application_method TEXT NULL;

ALTER TABLE support_programs
    ADD COLUMN application_start_date DATE NULL;

ALTER TABLE support_programs
    ADD COLUMN application_end_date DATE NULL;

ALTER TABLE support_programs
    ADD COLUMN application_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE support_programs
    ADD COLUMN official_source VARCHAR(255) NULL;

ALTER TABLE support_programs
    ADD COLUMN official_url VARCHAR(2048) NULL;
