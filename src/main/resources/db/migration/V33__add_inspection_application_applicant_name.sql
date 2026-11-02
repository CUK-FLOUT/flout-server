ALTER TABLE inspection_applications ADD COLUMN applicant_name VARCHAR(50) NULL;

UPDATE inspection_applications
SET applicant_name = (SELECT name FROM users WHERE users.user_id = inspection_applications.applicant_user_id);

ALTER TABLE inspection_applications MODIFY COLUMN applicant_name VARCHAR(50) NOT NULL;
