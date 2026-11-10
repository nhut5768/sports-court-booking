-- Mỗi đơn đã hoàn tất được khách đánh giá một lần (có thể sửa)
CREATE TABLE reviews (
  id BIGINT NOT NULL AUTO_INCREMENT,
  booking_id BIGINT NOT NULL,
  court_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  rating INT NOT NULL,
  comment VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reviews_booking (booking_id),
  KEY idx_reviews_court (court_id, created_at),
  CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
  CONSTRAINT fk_reviews_court FOREIGN KEY (court_id) REFERENCES courts (id) ON DELETE CASCADE,
  CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES accounts (id),
  CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
