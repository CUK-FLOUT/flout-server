ALTER TABLE inspections ADD COLUMN scheduled_by_user_id BIGINT NULL;
ALTER TABLE inspections ADD COLUMN assigned_at DATETIME NULL;
ALTER TABLE inspections
    ADD CONSTRAINT fk_inspections_scheduled_by FOREIGN KEY (scheduled_by_user_id) REFERENCES users(user_id);
