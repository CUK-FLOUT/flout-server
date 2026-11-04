ALTER TABLE support_applications
    MODIFY COLUMN status ENUM('SUBMITTED','REVIEWING','CONSENT_PENDING','SCHEDULING','APPROVED','REJECTED') NOT NULL DEFAULT 'REVIEWING';
ALTER TABLE support_application_status_histories
    MODIFY COLUMN previous_status ENUM('SUBMITTED','REVIEWING','CONSENT_PENDING','SCHEDULING','APPROVED','REJECTED') NOT NULL;
ALTER TABLE support_application_status_histories
    MODIFY COLUMN status ENUM('SUBMITTED','REVIEWING','CONSENT_PENDING','SCHEDULING','APPROVED','REJECTED') NOT NULL;

UPDATE support_applications SET status = 'REVIEWING' WHERE status = 'SUBMITTED';
UPDATE support_application_status_histories SET previous_status = 'REVIEWING' WHERE previous_status = 'SUBMITTED';
UPDATE support_application_status_histories SET status = 'REVIEWING' WHERE status = 'SUBMITTED';
