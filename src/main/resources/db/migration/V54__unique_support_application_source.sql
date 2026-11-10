-- Add first: existing duplicates stop migration without altering or deleting any rows
-- and leave the old constraint in place. Resolve duplicates explicitly before retrying.
ALTER TABLE support_applications
    ADD CONSTRAINT uk_support_application_source UNIQUE (application_id);

ALTER TABLE support_applications
DROP CONSTRAINT uk_support_application_program;