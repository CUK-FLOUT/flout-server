ALTER TABLE application_status_histories ADD COLUMN processed_by_user_id BIGINT NULL;
ALTER TABLE application_status_histories ADD CONSTRAINT fk_application_status_histories_actor
    FOREIGN KEY (processed_by_user_id) REFERENCES users(user_id);

UPDATE application_status_histories
SET processed_by_user_id = (
    SELECT applicant_user_id FROM inspection_applications
    WHERE inspection_applications.application_id = application_status_histories.application_id
)
WHERE status = 'SUBMITTED';
