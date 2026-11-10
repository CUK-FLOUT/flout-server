-- FLOUT-216: preserve stable source identity, category and imported source context.
-- These fields do NOT signify that an application is currently open.
-- Run only after V62 has been applied. Never edit an already executed migration.
ALTER TABLE support_programs ADD COLUMN external_code VARCHAR(100) NULL;
ALTER TABLE support_programs ADD COLUMN program_kind VARCHAR(30) NULL;
ALTER TABLE support_programs ADD COLUMN source_ids TEXT NULL;
ALTER TABLE support_programs ADD COLUMN source_verified_at DATE NULL;
ALTER TABLE support_programs ADD COLUMN source_metadata TEXT NULL;
ALTER TABLE support_programs ADD CONSTRAINT uk_support_program_external_code UNIQUE (external_code);

-- The older V61 entry is a facility-installation reference, not an OPEN 2026 call.
UPDATE support_programs
SET program_kind = 'FACILITY'
WHERE name = '부천시 침수방지시설 설치 지원'
  AND program_kind IS NULL;
