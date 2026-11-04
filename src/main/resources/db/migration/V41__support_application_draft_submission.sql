ALTER TABLE support_applications
    MODIFY COLUMN status ENUM('DRAFT','SUBMITTED','REVIEWING','CONSENT_PENDING','SCHEDULING','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT';
ALTER TABLE support_applications MODIFY COLUMN submitted_at DATETIME NULL;
ALTER TABLE support_applications ADD COLUMN created_at DATETIME NULL;

-- Historical submitted_at was set at creation. Preserve it exactly, including on legacy drafts.
UPDATE support_applications SET created_at = submitted_at;
ALTER TABLE support_applications MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE support_applications s
SET status = 'SUBMITTED'
WHERE s.status = 'REVIEWING'
  AND (s.occupancy_type IN ('OWNER', 'ETC') OR
       (s.occupancy_type = 'TENANT' AND EXISTS (
           SELECT 1 FROM support_attachments a
           WHERE a.support_application_id = s.support_application_id
             AND a.attachment_type = 'LANDLORD_CONSENT')));

UPDATE support_applications s
SET status = 'SUBMITTED'
WHERE s.status = 'CONSENT_PENDING' AND s.occupancy_type = 'TENANT'
  AND EXISTS (SELECT 1 FROM support_attachments a
              WHERE a.support_application_id = s.support_application_id
                AND a.attachment_type = 'LANDLORD_CONSENT');

-- A confirmed consent without its document is contradictory and requires review.
UPDATE support_applications s
SET status = 'DRAFT'
WHERE s.status = 'CONSENT_PENDING' AND s.occupancy_type = 'TENANT'
  AND s.landlord_consent_status <> 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM support_attachments a
                  WHERE a.support_application_id = s.support_application_id
                    AND a.attachment_type = 'LANDLORD_CONSENT');
