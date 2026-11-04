ALTER TABLE inspection_applications ADD COLUMN submitted_at DATETIME NULL;

-- Earlier migrations converted legacy SUBMITTED histories to RECEIVED, so a RECEIVED
-- history alone cannot establish the actual receipt time for existing applications.
