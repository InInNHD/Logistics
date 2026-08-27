CREATE TABLE sys_revoked_token (
    token_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_id),
    INDEX idx_revoked_token_expiry (expires_at),
    CONSTRAINT fk_revoked_token_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE TABLE sys_auth_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(40) NOT NULL,
    username VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    detail VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_auth_audit_created (created_at),
    INDEX idx_auth_audit_user_created (username, created_at)
);
