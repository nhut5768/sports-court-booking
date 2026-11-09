ALTER TABLE accounts
  ADD COLUMN bank_code VARCHAR(20) NULL,
  ADD COLUMN bank_account_no VARCHAR(30) NULL,
  ADD COLUMN bank_account_name VARCHAR(100) NULL;

ALTER TABLE bookings
  ADD COLUMN deposit_amount DECIMAL(12, 0) NULL,
  ADD COLUMN committed_at DATETIME NULL,
  ADD COLUMN deposit_sent_at DATETIME NULL,
  ADD COLUMN deposit_confirmed_at DATETIME NULL,
  ADD COLUMN deposit_forfeited BOOLEAN NOT NULL DEFAULT FALSE;
