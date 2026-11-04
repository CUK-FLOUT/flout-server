ALTER TABLE constructions
    ADD COLUMN final_installation_result VARCHAR(4000) NULL;

ALTER TABLE constructions
    ADD COLUMN completed_at DATETIME NULL;
