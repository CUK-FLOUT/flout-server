ALTER TABLE inspections ADD COLUMN inspector_user_id BIGINT NULL;
ALTER TABLE inspections ADD COLUMN pre_visit_notice TEXT NULL;
ALTER TABLE inspections
    ADD CONSTRAINT fk_inspections_inspector FOREIGN KEY (inspector_user_id) REFERENCES users(user_id);
