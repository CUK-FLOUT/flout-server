ALTER TABLE inspection_results ADD COLUMN published_by_user_id BIGINT NULL;
ALTER TABLE inspection_results ADD CONSTRAINT fk_inspection_result_publisher
    FOREIGN KEY (published_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL;

CREATE TABLE construction_status_histories (
    construction_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
    construction_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT NULL,
    actor_name VARCHAR(50) NULL,
    change_source VARCHAR(20) NOT NULL,
    changed_at DATETIME NOT NULL,
    PRIMARY KEY (construction_status_history_id),
    CONSTRAINT uk_construction_history_status UNIQUE (construction_id, status),
    CONSTRAINT fk_construction_history_construction FOREIGN KEY (construction_id)
        REFERENCES constructions(construction_id) ON DELETE CASCADE,
    CONSTRAINT fk_construction_history_actor FOREIGN KEY (changed_by_user_id)
        REFERENCES users(user_id) ON DELETE SET NULL,
    INDEX idx_construction_history_time (construction_id, changed_at)
);

-- Only a persisted completion timestamp proves a legacy event. Do not invent
-- assignment/start timestamps from scheduled_at or mutable updated_at.
INSERT INTO construction_status_histories (construction_id, status, change_source, changed_at)
SELECT construction_id, 'COMPLETED', 'LEGACY', completed_at
FROM constructions WHERE status = 'COMPLETED' AND completed_at IS NOT NULL;
