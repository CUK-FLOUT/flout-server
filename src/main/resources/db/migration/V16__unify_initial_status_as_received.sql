-- Expand inspection enums before replacing legacy values.
ALTER TABLE inspections MODIFY COLUMN status
    ENUM('WAITING', 'RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'RECEIVED';
ALTER TABLE inspection_status_histories MODIFY COLUMN previous_status
    ENUM('WAITING', 'RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL;
ALTER TABLE inspection_status_histories MODIFY COLUMN status
    ENUM('WAITING', 'RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL;

UPDATE inspection_applications SET status = 'RECEIVED' WHERE status = 'SUBMITTED';
UPDATE application_status_histories SET status = 'RECEIVED' WHERE status = 'SUBMITTED';
-- Preserve the original audit timestamp despite MySQL ON UPDATE behavior.
UPDATE inspections SET status = 'RECEIVED', updated_at = updated_at WHERE status = 'WAITING';
UPDATE inspection_status_histories SET previous_status = 'RECEIVED' WHERE previous_status = 'WAITING';
UPDATE inspection_status_histories SET status = 'RECEIVED' WHERE status = 'WAITING';

ALTER TABLE inspection_applications MODIFY COLUMN status ENUM('RECEIVED') NOT NULL DEFAULT 'RECEIVED';
ALTER TABLE application_status_histories MODIFY COLUMN status ENUM('RECEIVED') NOT NULL;
ALTER TABLE inspections MODIFY COLUMN status
    ENUM('RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'RECEIVED';
ALTER TABLE inspection_status_histories MODIFY COLUMN previous_status
    ENUM('RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL;
ALTER TABLE inspection_status_histories MODIFY COLUMN status
    ENUM('RECEIVED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED') NOT NULL;

-- Older submissions may not have had a corresponding inspection yet.
INSERT INTO inspections (application_id, status)
SELECT a.application_id, 'RECEIVED'
FROM inspection_applications a
WHERE NOT EXISTS (SELECT 1 FROM inspections i WHERE i.application_id = a.application_id);
