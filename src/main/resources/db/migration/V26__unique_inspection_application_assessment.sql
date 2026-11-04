ALTER TABLE inspection_applications
    ADD CONSTRAINT uk_inspection_applications_assessment_id UNIQUE (assessment_id);
