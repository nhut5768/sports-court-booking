-- Đơn chờ cọc chỉ giữ khung giờ đến hold_until; quá hạn thì tự hủy
ALTER TABLE bookings ADD COLUMN hold_until DATETIME NULL;
UPDATE bookings SET hold_until = DATE_ADD(NOW(), INTERVAL 30 MINUTE) WHERE status = 'PENDING_DEPOSIT';

-- Mật khẩu tạm do chủ sân cấp: bắt buộc đổi sau khi đăng nhập
ALTER TABLE accounts ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Chủ sân trả lời đánh giá
ALTER TABLE reviews
  ADD COLUMN owner_reply VARCHAR(1000) NULL,
  ADD COLUMN replied_at DATETIME NULL;
