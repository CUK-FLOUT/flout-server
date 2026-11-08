ALTER TABLE prevention_guides ADD COLUMN resolution VARCHAR(32) NULL;
UPDATE prevention_guides SET resolution = 'CONFIRMED' WHERE is_resolved = TRUE;
UPDATE prevention_guides SET resolution = 'NEEDS_INSPECTION' WHERE is_resolved = FALSE AND unresolved_detail IS NOT NULL AND TRIM(unresolved_detail) <> '';

CREATE TABLE prevention_guide_items (
    item_id BIGINT NOT NULL AUTO_INCREMENT,
    guide_id BIGINT NOT NULL,
    item_order INT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (item_id),
    CONSTRAINT uk_guide_item_order UNIQUE (guide_id, item_order),
    CONSTRAINT fk_guide_item_guide FOREIGN KEY (guide_id) REFERENCES prevention_guides(guide_id) ON DELETE CASCADE
);
