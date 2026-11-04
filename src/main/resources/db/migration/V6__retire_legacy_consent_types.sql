CREATE TABLE IF NOT EXISTS user_consent_migration_conflicts (
    consent_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    original_consent_type VARCHAR(32) NOT NULL,
    target_consent_type VARCHAR(32) NOT NULL,
    version VARCHAR(20) NOT NULL,
    is_agreed BOOLEAN NOT NULL,
    agreed_at DATETIME NOT NULL,
    PRIMARY KEY (consent_id)
);

INSERT INTO user_consent_migration_conflicts
    (consent_id, user_id, original_consent_type, target_consent_type, version, is_agreed, agreed_at)
SELECT old.consent_id, old.user_id, old.consent_type, newer.consent_type,
       old.version, old.is_agreed, old.agreed_at
FROM user_consents old
JOIN user_consents newer ON newer.user_id = old.user_id AND newer.version = old.version
WHERE ((old.consent_type = 'TERMS' AND newer.consent_type = 'THIRD_PARTY_PROVISION')
    OR (old.consent_type = 'PRIVACY' AND newer.consent_type = 'STATUS_NOTIFICATION'))
  AND NOT EXISTS (
      SELECT 1 FROM user_consent_migration_conflicts saved WHERE saved.consent_id = old.consent_id
  );

DELETE FROM user_consents
WHERE consent_type IN ('TERMS', 'PRIVACY')
  AND consent_id IN (SELECT consent_id FROM user_consent_migration_conflicts);

UPDATE user_consents
SET consent_type = CASE consent_type
    WHEN 'TERMS' THEN 'THIRD_PARTY_PROVISION'
    WHEN 'PRIVACY' THEN 'STATUS_NOTIFICATION'
    ELSE consent_type
END
WHERE consent_type IN ('TERMS', 'PRIVACY');

ALTER TABLE user_consents MODIFY COLUMN consent_type
    ENUM('THIRD_PARTY_PROVISION', 'STATUS_NOTIFICATION') NOT NULL;
