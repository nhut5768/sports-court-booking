ALTER TABLE accounts
  ADD COLUMN payment_qr VARCHAR(80) NULL,
  ADD COLUMN payment_qr_type VARCHAR(80) NULL;

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_id BIGINT NOT NULL,
  booking_id BIGINT NULL,
  message VARCHAR(300) NOT NULL,
  created_at DATETIME NOT NULL,
  read_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_notifications_account (account_id, read_at),
  CONSTRAINT fk_notifications_account FOREIGN KEY (account_id) REFERENCES accounts (id),
  CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
