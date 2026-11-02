CREATE TABLE accounts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(190) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_accounts_email (email),
  KEY idx_accounts_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE courts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  sport VARCHAR(50) NOT NULL,
  area VARCHAR(100) NOT NULL,
  address VARCHAR(250) NOT NULL,
  description VARCHAR(2000) NULL,
  amenities VARCHAR(1000) NULL,
  price DECIMAL(12, 0) NOT NULL,
  min_price DECIMAL(12, 0) NOT NULL,
  open_hour INT NOT NULL,
  close_hour INT NOT NULL,
  active BOOLEAN NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_courts_owner (owner_id),
  KEY idx_courts_search (active, sport, area),
  KEY idx_courts_min_price (min_price),
  CONSTRAINT fk_courts_owner FOREIGN KEY (owner_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE price_rules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  court_id BIGINT NOT NULL,
  start_hour INT NOT NULL,
  end_hour INT NOT NULL,
  price DECIMAL(12, 0) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_price_rules_court (court_id),
  CONSTRAINT fk_price_rules_court FOREIGN KEY (court_id) REFERENCES courts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE court_images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  court_id BIGINT NOT NULL,
  stored_name VARCHAR(80) NOT NULL,
  content_type VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_court_images_stored (stored_name),
  KEY idx_court_images_court (court_id),
  CONSTRAINT fk_court_images_court FOREIGN KEY (court_id) REFERENCES courts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bookings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  court_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  play_date DATE NOT NULL,
  start_hour INT NOT NULL,
  amount DECIMAL(12, 0) NOT NULL,
  status VARCHAR(20) NOT NULL,
  active_slot_key VARCHAR(80) NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bookings_active_slot (active_slot_key),
  KEY idx_bookings_customer (customer_id, created_at),
  KEY idx_bookings_court_date (court_id, play_date),
  KEY idx_bookings_status_date (status, play_date),
  CONSTRAINT fk_bookings_court FOREIGN KEY (court_id) REFERENCES courts (id),
  CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE blocked_slots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  court_id BIGINT NOT NULL,
  play_date DATE NOT NULL,
  start_hour INT NOT NULL,
  slot_key VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_blocked_slots_key (slot_key),
  KEY idx_blocked_slots_court_date (court_id, play_date),
  CONSTRAINT fk_blocked_slots_court FOREIGN KEY (court_id) REFERENCES courts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
