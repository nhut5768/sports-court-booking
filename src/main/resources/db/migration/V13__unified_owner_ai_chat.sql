-- Preserve existing court conversations. New conversations can target an owner directly.
ALTER TABLE court_conversations
  MODIFY COLUMN court_id BIGINT NULL,
  ADD COLUMN direct_key VARCHAR(80) NULL,
  ADD COLUMN ai_paused BOOLEAN NOT NULL DEFAULT FALSE,
  ADD UNIQUE KEY uk_chat_direct (direct_key);
ALTER TABLE court_chat_messages
  MODIFY COLUMN sender_id BIGINT NULL,
  ADD COLUMN message_kind VARCHAR(16) NOT NULL DEFAULT 'HUMAN',
  ADD COLUMN ai_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
  ADD COLUMN ai_reply_to BIGINT NULL,
  ADD COLUMN ai_links TEXT NULL,
  ADD UNIQUE KEY uk_chat_ai_reply (ai_reply_to);
