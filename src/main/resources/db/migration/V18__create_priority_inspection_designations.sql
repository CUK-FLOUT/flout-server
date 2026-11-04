CREATE TABLE priority_inspection_designations (
    application_id BIGINT NOT NULL,
    selection_memo TEXT NULL,
    designated_by_user_id BIGINT NOT NULL,
    designated_at DATETIME NOT NULL,
    PRIMARY KEY (application_id),
    CONSTRAINT fk_priority_designations_application FOREIGN KEY (application_id)
        REFERENCES inspection_applications(application_id),
    CONSTRAINT fk_priority_designations_user FOREIGN KEY (designated_by_user_id)
        REFERENCES users(user_id)
);
