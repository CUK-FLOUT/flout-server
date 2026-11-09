CREATE TABLE resident_form_drafts (
    draft_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    draft_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    form_state VARCHAR(20) NOT NULL,
    fields_json TEXT NOT NULL,
    version BIGINT NOT NULL,
    linked_id BIGINT NULL,
    submission_hash VARCHAR(64) NULL,
    submission_response TEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (draft_id),
    CONSTRAINT fk_form_draft_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_form_draft_user_source (user_id, draft_type, source_id, form_state)
);

ALTER TABLE prevention_guides ADD COLUMN progress_updated_at DATETIME NULL;
