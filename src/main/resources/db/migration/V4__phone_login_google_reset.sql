-- Số điện thoại: di động Việt Nam 10 số, duy nhất, dùng để đăng nhập.
-- Tài khoản Google có thể chưa có số nên cho phép NULL.
ALTER TABLE accounts MODIFY COLUMN phone VARCHAR(20) NULL;

UPDATE accounts SET phone = NULL WHERE phone NOT REGEXP '^0[35789][0-9]{8}$';

-- Số trùng: giữ cho tài khoản tạo sớm nhất, các tài khoản còn lại cập nhật lại sau
UPDATE accounts a
  JOIN (
    SELECT phone, MIN(id) AS keep_id FROM accounts
    WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1
  ) d ON a.phone = d.phone AND a.id <> d.keep_id
SET a.phone = NULL;

ALTER TABLE accounts
  ADD COLUMN google_sub VARCHAR(64) NULL,
  ADD UNIQUE KEY uk_accounts_phone (phone),
  ADD UNIQUE KEY uk_accounts_google (google_sub);

CREATE TABLE password_reset_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_password_reset_hash (token_hash),
  KEY idx_password_reset_account (account_id),
  CONSTRAINT fk_password_reset_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
