ALTER TABLE risk_assessments
    ADD COLUMN housing_type ENUM('SEMI_BASEMENT', 'FIRST_FLOOR', 'SECOND_FLOOR_OR_ABOVE') NULL;

UPDATE risk_assessments
SET housing_type = (
    SELECT CONCAT('', application.housing_type)
    FROM inspection_applications application
    WHERE application.assessment_id = risk_assessments.assessment_id
      AND application.housing_type IS NOT NULL
)
WHERE EXISTS (
    SELECT 1
    FROM inspection_applications application
    WHERE application.assessment_id = risk_assessments.assessment_id
      AND application.housing_type IS NOT NULL
);

ALTER TABLE inspection_applications DROP COLUMN housing_type;
