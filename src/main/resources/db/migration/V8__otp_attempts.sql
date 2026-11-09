-- Quên mật khẩu bằng mã OTP qua số điện thoại: đếm số lần nhập sai
DELETE FROM password_reset_tokens;
ALTER TABLE password_reset_tokens ADD COLUMN attempts INT NOT NULL DEFAULT 0;
