ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject);
