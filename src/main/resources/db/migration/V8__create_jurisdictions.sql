CREATE TABLE jurisdictions (
    jurisdiction_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (jurisdiction_id),
    CONSTRAINT uk_jurisdictions_name UNIQUE (name)
);

INSERT INTO jurisdictions (name) VALUES ('부천시');

ALTER TABLE users ADD COLUMN jurisdiction_id BIGINT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_jurisdiction
    FOREIGN KEY (jurisdiction_id) REFERENCES jurisdictions(jurisdiction_id);

UPDATE users
SET jurisdiction_id = (SELECT jurisdiction_id FROM jurisdictions WHERE name = '부천시')
WHERE role = 'ADMIN';
