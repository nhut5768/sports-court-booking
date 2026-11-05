-- KAN-16 | T5 tuan 2
-- Kiem tra khoa ngoai, kieu du lieu va rang buoc entity-SQL.
-- Chi doc metadata. Chon dung database truoc khi chay.
-- Doi chieu voi 13 entity va schema sau migration V15.
-- Chua phai ket qua kiem thu; can luu dau ra sau khi thuc thi.

SELECT DATABASE() AS database_dang_kiem_tra;

-- 1. Kieu du lieu, do dai, NULL, DEFAULT va collation
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
       COLUMN_DEFAULT, EXTRA, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 2. Khoa ngoai va quy tac cap nhat/xoa
SELECT k.TABLE_NAME, k.COLUMN_NAME, k.CONSTRAINT_NAME,
       k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
       r.UPDATE_RULE, r.DELETE_RULE
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS r
  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
 AND r.TABLE_NAME = k.TABLE_NAME
 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION;

-- 3. Khoa chinh va chi muc duy nhat
SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX,
       COLUMN_NAME, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND NON_UNIQUE = 0
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 4. CHECK va trang thai thuc thi
SELECT t.TABLE_NAME, t.CONSTRAINT_NAME,
       c.CHECK_CLAUSE, t.ENFORCED
FROM information_schema.TABLE_CONSTRAINTS t
JOIN information_schema.CHECK_CONSTRAINTS c
  ON c.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA
 AND c.CONSTRAINT_NAME = t.CONSTRAINT_NAME
WHERE t.TABLE_SCHEMA = DATABASE()
  AND t.CONSTRAINT_TYPE = 'CHECK'
ORDER BY t.TABLE_NAME, t.CONSTRAINT_NAME;

-- Diem can doi chieu:
-- Gia: DECIMAL(12,0); ngay: DATE; thoi gian: DATETIME/DATETIME(6).
-- phone/google_sub, conversation.court_id, message.sender_id nullable.
-- active_slot_key va slot_key UNIQUE o hai bang rieng.
-- reviews.rating co CHECK 1..5; ai_reply_to UNIQUE nhung chua co FK.
-- Khong sua migration V1-V15 da chay.
