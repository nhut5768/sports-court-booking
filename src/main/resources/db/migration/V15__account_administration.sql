ALTER TABLE accounts ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE admin_control (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO admin_control(id) VALUES (1);
CREATE TABLE admin_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 target_id BIGINT NOT NULL,
 action VARCHAR(40) NOT NULL,
 detail VARCHAR(250) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_admin_audit_actor FOREIGN KEY(actor_id) REFERENCES accounts(id),
 CONSTRAINT fk_admin_audit_target FOREIGN KEY(target_id) REFERENCES accounts(id),
 KEY idx_admin_audit_created(created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
