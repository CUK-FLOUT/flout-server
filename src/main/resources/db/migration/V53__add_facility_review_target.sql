ALTER TABLE facility_review ADD COLUMN review_target VARCHAR(100) NULL;

UPDATE facility_review
SET review_target = (
    SELECT public_facility.facility_name
    FROM public_facility
    WHERE public_facility.facility_id = facility_review.facility_id
);

ALTER TABLE facility_review MODIFY COLUMN review_target VARCHAR(100) NOT NULL;
ALTER TABLE facility_review MODIFY COLUMN facility_id BIGINT NULL;
