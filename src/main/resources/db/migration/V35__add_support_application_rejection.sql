ALTER TABLE support_applications
    MODIFY COLUMN status ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL DEFAULT 'SUBMITTED';
ALTER TABLE support_applications ADD COLUMN rejection_reason VARCHAR(500) NULL;
ALTER TABLE support_application_status_histories
    MODIFY COLUMN previous_status ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL;
ALTER TABLE support_application_status_histories
    MODIFY COLUMN status ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL;
ALTER TABLE support_application_status_histories ADD COLUMN rejection_reason VARCHAR(500) NULL;
