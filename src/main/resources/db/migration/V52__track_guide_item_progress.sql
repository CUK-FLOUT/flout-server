ALTER TABLE prevention_guide_items ADD COLUMN progress_recorded BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE prevention_guide_items SET progress_recorded = TRUE WHERE completed = TRUE;
