# Thành viên lấy mã nguồn và chạy local

Repo: https://github.com/nhut5768/san-nha-team ; bản đang chia sẻ: **main**. Không force push nhánh dùng chung. Repo có thể có tích hợp tự deploy; thay đổi cấu hình hosting là việc riêng.

## Lấy mã nguồn lần đầu

    git clone https://github.com/nhut5768/san-nha-team.git
    cd san-nha-team

Máy cần JDK 21 và MySQL local (8.4 trở lên); Maven Wrapper đã có trong repo. Trong Workbench chạy database/setup-local.sql, tạo user court_app với mật khẩu riêng và cấp quyền theo file, rồi:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1 -DatabaseName sports_court_booking

Truy cập http://localhost:8082. Mỗi máy tự nhập credentials bằng lời nhắc ẩn; các lần sau dùng kho Windows của máy đó. Chưa có Google/Gemini thì để trống các lời nhắc tương ứng. Đăng nhập, đăng ký, đặt sân và chat chủ sân không phụ thuộc Gemini. Hướng dẫn MySQL và restore: [database/README.md](../database/README.md).

Mỗi thành viên có database local riêng. Clone/pull không đồng bộ dữ liệu giữa các máy. Không có tài khoản hoặc mật khẩu mặc định; chủ sân và khách tự đăng ký, admin đầu tiên dùng scripts/bootstrap-admin.ps1 theo [ACCOUNT_ADMIN.md](ACCOUNT_ADMIN.md). Chỉ dùng dữ liệu sân do chủ sân cung cấp/xác nhận, không coi danh sách demo cũ là dữ liệu kinh doanh đã xác minh.

Nếu cần cùng dữ liệu: trưởng nhóm dừng web, chạy scripts/backup-local.ps1 và gửi thư mục backup riêng cho thành viên được phép; restore vào schema mới trống và phục hồi ảnh. Không đưa SQL chứa dữ liệu cá nhân hoặc ảnh/QR upload vào Git.

## Cập nhật và làm việc nhóm

Đảm bảo đã commit/cất thay đổi cá nhân trước khi cập nhật:

    git switch main
    git pull --ff-only origin main
    git switch -c feature/ten-cong-viec

Kiểm thử trước khi đẩy:

    .\mvnw.cmd verify
    git add DUONG_DAN_DA_SUA
    git commit -m "feat: mo ta thay doi"
    git push -u origin feature/ten-cong-viec

Sau đó tạo pull request theo quy trình nhóm. Không force push nhánh dùng chung. Không commit .env, API key, password, uploads, target hoặc backup; kiểm tra git diff --cached trước khi commit.

Bản mới bỏ theo dõi dump và uploads từng có trong repo; file local vẫn giữ nguyên. Các commit cũ có thể vẫn chứa các file này. Không dùng mật khẩu demo cũ cho tài khoản thật; việc loại bỏ dữ liệu khỏi toàn bộ lịch sử là một tác vụ riêng cần phối hợp với nhóm.
