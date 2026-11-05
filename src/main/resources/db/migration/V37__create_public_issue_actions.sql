ALTER TABLE public_issues
    MODIFY COLUMN status ENUM('RECEIVED','UNDER_REVIEW','INSPECTION_REQUIRED','ACTION_IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'RECEIVED';

CREATE TABLE public_facility (
    facility_id BIGINT NOT NULL AUTO_INCREMENT,
    facility_name VARCHAR(100) NOT NULL,
    facility_type VARCHAR(50) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (facility_id)
);

CREATE TABLE facility_review (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    local_problem_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    inspection_note TEXT NULL,
    review_status ENUM('PENDING','UNDER_REVIEW','INSPECTION_REQUIRED','ACTION_IN_PROGRESS','COMPLETED') NULL,
    review_reason TEXT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (review_id),
    CONSTRAINT uk_facility_review_local_problem UNIQUE (local_problem_id),
    CONSTRAINT fk_facility_review_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_facility_review_local_problem FOREIGN KEY (local_problem_id)
        REFERENCES public_issues(public_issue_id),
    CONSTRAINT fk_facility_review_facility FOREIGN KEY (facility_id)
        REFERENCES public_facility(facility_id)
);

CREATE TABLE facility_action (
    action_id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    action_description TEXT NOT NULL,
    action_status ENUM('IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (action_id),
    CONSTRAINT fk_facility_action_review FOREIGN KEY (review_id)
        REFERENCES facility_review(review_id),
    INDEX idx_facility_action_review_created (review_id, created_at, action_id)
);

CREATE TABLE facility_action_image (
    image_id BIGINT NOT NULL AUTO_INCREMENT,
    image_url VARCHAR(500) NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action_id BIGINT NOT NULL,
    PRIMARY KEY (image_id),
    CONSTRAINT fk_facility_action_image_action FOREIGN KEY (action_id)
        REFERENCES facility_action(action_id),
    INDEX idx_facility_action_image_action (action_id, image_id)
);
