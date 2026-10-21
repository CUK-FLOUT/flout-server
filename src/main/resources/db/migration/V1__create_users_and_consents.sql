CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role ENUM('RESIDENT', 'ADMIN') NOT NULL DEFAULT 'RESIDENT',
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE user_consents (
    consent_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    consent_type ENUM('TERMS', 'PRIVACY') NOT NULL,
    version VARCHAR(20) NOT NULL,
    is_agreed BOOLEAN NOT NULL DEFAULT TRUE,
    agreed_at DATETIME NOT NULL,
    PRIMARY KEY (consent_id),
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_user_consent_version UNIQUE (user_id, consent_type, version)
);
