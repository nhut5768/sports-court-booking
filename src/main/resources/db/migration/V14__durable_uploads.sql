-- Render Free has no persistent disk. Store originals in the existing MySQL database.
CREATE TABLE stored_uploads (
  object_key VARCHAR(160) NOT NULL,
  content LONGBLOB NOT NULL,
  PRIMARY KEY (object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
