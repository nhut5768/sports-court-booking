-- Existing accounts remain unlinked. Subject identifiers are case sensitive.
ALTER TABLE accounts ADD COLUMN google_sub VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD UNIQUE KEY uk_accounts_google (google_sub);
