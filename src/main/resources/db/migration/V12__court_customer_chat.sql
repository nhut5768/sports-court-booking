CREATE TABLE court_conversations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  court_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  customer_read_through BIGINT NOT NULL DEFAULT 0,
  owner_read_through BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_customer_court (customer_id, court_id),
  KEY idx_chat_owner_updated (owner_id, updated_at),
  KEY idx_chat_customer_updated (customer_id, updated_at),
  CONSTRAINT fk_chat_court FOREIGN KEY (court_id) REFERENCES courts(id),
  CONSTRAINT fk_chat_customer FOREIGN KEY (customer_id) REFERENCES accounts(id),
  CONSTRAINT fk_chat_owner FOREIGN KEY (owner_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE court_chat_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  client_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content VARCHAR(1000) NOT NULL,
  sent_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_retry (conversation_id, sender_id, client_id),
  KEY idx_chat_history (conversation_id, id),
  CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES court_conversations(id),
  CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
