ALTER TABLE priority_inspection_designations ADD COLUMN selection_order INT NOT NULL DEFAULT 0;
ALTER TABLE priority_inspection_designations ADD COLUMN order_updated_by_user_id BIGINT NULL;
ALTER TABLE priority_inspection_designations ADD COLUMN order_updated_at DATETIME NULL;
ALTER TABLE priority_inspection_designations
    ADD CONSTRAINT fk_priority_order_updated_by FOREIGN KEY (order_updated_by_user_id)
        REFERENCES users(user_id);

CREATE TABLE priority_inspection_order_backfill AS
SELECT application_id, ROW_NUMBER() OVER (ORDER BY designated_at, application_id) AS selection_order
FROM priority_inspection_designations;

UPDATE priority_inspection_designations
SET selection_order = (SELECT selection_order FROM priority_inspection_order_backfill
    WHERE priority_inspection_order_backfill.application_id = priority_inspection_designations.application_id);

DROP TABLE priority_inspection_order_backfill;
