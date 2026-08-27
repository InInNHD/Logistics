CREATE TABLE sys_security_guard (
    id BIGINT NOT NULL,
    guard_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_security_guard_name UNIQUE (guard_name)
);

INSERT INTO sys_security_guard (id, guard_name) VALUES (1, 'ADMIN_SET');
