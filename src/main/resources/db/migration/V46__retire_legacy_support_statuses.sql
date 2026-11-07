-- Resolve historical in-flight states before narrowing the enums.
UPDATE support_applications s
SET status = COALESCE(
    (SELECT CONCAT('', h.status)
     FROM support_application_status_histories h
     WHERE h.support_application_id = s.support_application_id
       AND h.status IN ('APPROVED', 'REJECTED')
     ORDER BY h.changed_at DESC, h.support_application_status_history_id DESC
     LIMIT 1),
    CASE
        WHEN EXISTS (SELECT 1 FROM constructions c
                     WHERE c.support_application_id = s.support_application_id)
            THEN 'APPROVED'
        WHEN s.occupancy_type = 'TENANT'
             AND NOT EXISTS (SELECT 1 FROM support_attachments a
                             WHERE a.support_application_id = s.support_application_id
                               AND a.attachment_type = 'LANDLORD_CONSENT')
            THEN 'DRAFT'
        ELSE 'SUBMITTED'
    END)
WHERE s.status IN ('REVIEWING', 'CONSENT_PENDING', 'SCHEDULING');

UPDATE support_application_status_histories SET previous_status = 'SUBMITTED'
WHERE previous_status IN ('REVIEWING', 'CONSENT_PENDING', 'SCHEDULING');
UPDATE support_application_status_histories SET status = 'SUBMITTED'
WHERE status IN ('REVIEWING', 'CONSENT_PENDING', 'SCHEDULING');

ALTER TABLE support_applications
    MODIFY COLUMN status ENUM('DRAFT','SUBMITTED','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT';
ALTER TABLE support_application_status_histories
    MODIFY COLUMN previous_status ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL;
ALTER TABLE support_application_status_histories
    MODIFY COLUMN status ENUM('SUBMITTED','APPROVED','REJECTED') NOT NULL;
