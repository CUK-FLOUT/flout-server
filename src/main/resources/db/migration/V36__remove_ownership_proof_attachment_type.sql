ALTER TABLE support_attachments
    ADD CONSTRAINT ck_support_attachments_landlord_only CHECK (attachment_type = 'LANDLORD_CONSENT');

ALTER TABLE support_attachments
    MODIFY COLUMN attachment_type ENUM('LANDLORD_CONSENT') NOT NULL;
