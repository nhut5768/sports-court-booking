-- Hibernate kiểm tra schema mong đợi VARCHAR cho token_hash
ALTER TABLE password_reset_tokens MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;
