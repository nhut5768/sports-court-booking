-- Bỏ đăng nhập Google, chỉ giữ đăng nhập bằng email hoặc số điện thoại
ALTER TABLE accounts DROP INDEX uk_accounts_google, DROP COLUMN google_sub;
