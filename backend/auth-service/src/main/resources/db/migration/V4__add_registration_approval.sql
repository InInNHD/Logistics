ALTER TABLE sys_user
    ADD COLUMN registration_pending BOOLEAN NOT NULL DEFAULT FALSE AFTER enabled;

CREATE INDEX idx_sys_user_registration_pending ON sys_user (registration_pending, created_at);
