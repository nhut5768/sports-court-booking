# Chuyển dữ liệu hiện có từ local sang Aiven — không seed

## Kết quả đã kiểm tra tại máy ngày 02/11/2026

- Local: sports_court_booking, schema Flyway V1–V13 thành công.
- 4 sân đều active=true, owner_id=1:
  1. Cầu lông Bình Minh – Sân 01, Bình Thạnh.
  2. Sân bóng Thành Phố – Sân 01, Thủ Đức.
  3. Tennis Riverside – Sân 01, Quận 7.
  4. Bóng rổ Central – Sân 01, Gò Vấp.
- 5 tài khoản, 10 booking, 4 court_images, 4 price_rules, 1 blocked_slot,
  3 hội thoại, 8 tin nhắn, 3 thông báo, 0 review.
- 4 file ảnh sân được metadata tham chiếu, tổng 11.558.123 byte. Đã đọc được đủ file.
- Một file QR trong uploads/payment-qr không được account nào tham chiếu.
  Archive giữ lại file đó, nhưng không tự gán QR cho tài khoản hoặc tạo metadata mới.
- GET Render không bộ lọc trả 200, không có link /courts/<id> và có trạng thái trống.
  Điều này chỉ chứng minh không có sân hiển thị, chưa chứng minh Aiven hoàn toàn trống.
- Chưa đọc/ghi Aiven vì mật khẩu chỉ đang nằm trên Render; không yêu cầu gửi mật khẩu qua chat.

## Backup đã có, KHÔNG đưa lên Git

Bản mysqldump sạch đã xác minh hoàn tất:
backups/local-before-aiven-verified-20261102-155618/database.sql, uploads.zip, checksums.json.

Snapshot JDBC nhất quán, không dùng mysqldump để import vào schema đích:
backups/aiven-prepared-20261102-160146/source.sql và uploads.zip.
Archive gồm local-files/ (tất cả ảnh, kể cả QR không tham chiếu) và mysql-originals/ (4 ảnh gốc cần lưu trong MySQL).
source.sql chứa cả hash mật khẩu và dữ liệu cá nhân, chỉ lưu riêng. Không gửi file lên Git hoặc chat.
Không dùng các bản backup thử khác thay cho hai thư mục được chỉ rõ trên.

Công cụ tạo snapshot mới ở mỗi lần Prepare/Transfer, không lấy dữ liệu mẫu và không import SQL trực tiếp.
Mật khẩu chỉ nhập bằng Read-Host -AsSecureString, truyền qua environment của tiến trình con rồi phục hồi/xóa khi kết thúc.
Không dùng mật khẩu ở command line, .env, transcript hoặc log.

## 1. Chỉ kiểm tra đích trước

Mở PowerShell tại E:\HKDN\san-nha-team:

    .\scripts\aiven-data.ps1 -Mode Inspect

Script hỏi mật khẩu local, đường dẫn file CA đã tải từ Aiven và mật khẩu Aiven bằng ô nhập ẩn.
Nếu đã biết đường dẫn CA:

    .\scripts\aiven-data.ps1 -Mode Inspect -CaPath "$env:USERPROFILE\Downloads\ca.pem"

Đường dẫn là ví dụ, thay bằng file thực tế. Nếu Windows chặn chạy script, chỉ cho phép tiến trình hiện tại:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\aiven-data.ps1 -Mode Inspect

Tool tự import CA vào truststore riêng trong backups/, dùng sslMode=VERIFY_IDENTITY để xác minh CA và hostname
mysql-3bbe504b-ut-59dc.l.aivencloud.com:23004/defaultdb, user avnadmin. Không tắt SSL, không sửa thông tin Render.
Không in mật khẩu, email khách hay nội dung tin nhắn. Inspect chỉ đọc số lượng bảng và tên/ID/trạng thái sân.

Bạn có thể gửi lại stdout (counts và court lines). Không gửi mật khẩu, source.sql, ZIP hoặc environment dump.

## 2. Nếu Aiven đã có dữ liệu

- Không chỉ nhìn courts: accounts, bookings, chat và stored_uploads có dữ liệu cũng là đích không trống.
- Tool Transfer sẽ backup đích rồi DỪNG nếu bất kỳ bảng nghiệp vụ nào không trống, không UPDATE/DELETE/REPLACE.
- Không dùng mysql < source.sql, không tắt foreign_key_checks để ép import.
- Nếu chỉ có tài khoản đăng nhập thử trên Aiven: cũng cần kế hoạch merge ID/email/Google sub riêng; không ghi đè.
- Nếu Aiven đã có sân: kiểm tra các dòng active, đúng database defaultdb, owner_id và số court_images.
  Truy vấn trang chủ chỉ lấy active=true; query mặc định không lọc chủ sân hoặc ngày.
  Các bộ lọc sport/area/giá/từ khóa chỉ áp dụng khi URL có giá trị; thử https://san-nha-team.onrender.com/ không query.
  Ngày tìm kiếm chỉ thêm thông tin lịch, không xóa sân khỏi danh sách.
- Nếu active=true nhưng homepage vẫn trống, so DB_URL/DB_USERNAME của Render với đúng service này,
  rồi kiểm tra log lỗi query và build đang deploy; không tự đổi active hoặc tạo sân mới để che lỗi.

## 3. Nếu TẤT CẢ bảng nghiệp vụ trên Aiven trống

Kiểm tra app Render đã chạy migration V14 (stored_uploads tồn tại). Không sửa flyway_schema_history.
Dừng local app, tạm Suspend service Render để không phát sinh đăng ký/booking/chat trong lúc chuyển.
Suspend không xóa service hoặc database. Sau đó chạy:

    .\scripts\aiven-data.ps1 -Mode Transfer -CaPath "$env:USERPROFILE\Downloads\ca.pem" -ConfirmApplicationsPaused

Nếu không muốn chuyển, chỉ tạo backup mới bằng:

    .\scripts\aiven-data.ps1 -Mode Prepare

Transfer thực hiện:
1. Đọc snapshot local trong transaction REPEATABLE_READ, lấy đủ metadata và bytes ảnh; thiếu file thì dừng.
2. Backup source.sql và uploads.zip vào thư mục mới private, không overwrite backup cũ.
3. Kết nối đích TLS, dùng SERIALIZABLE và locking reads, backup target-before.sql.
4. Kiểm tra TẤT CẢ bảng nghiệp vụ đích trống, schema/columns và checksum migration nguồn khớp với đích.
   Flyway của đích được giữ nguyên, không copy lịch sử local lên.
5. INSERT bản ghi theo thứ tự foreign key, giữ nguyên ID tài khoản/chủ sân/sân/giá/khóa giờ/booking/chat.
6. INSERT ảnh vào stored_uploads đúng object_key courts/<courtId>/<storedName> hoặc payment-qr/<ownerId>/<storedName>.
   Ưu tiên ảnh gốc đã có ở stored_uploads nguồn, nếu không thì đọc uploads. Không gán file QR mồ côi.
7. Kiểm tra row counts và SHA-256 toàn bộ ảnh gốc trước COMMIT.
   Bất kỳ lỗi nào trong thao tác insert/verify sẽ ROLLBACK toàn bộ; không thay DDL/Flyway hoặc tắt FK ở đích.

Tool có giới hạn 50.000 rows, 128 MiB ảnh để phục vụ demo; quá giới hạn phải dùng kế hoạch migration riêng.
Backup đích/source SQL chỉ dùng khôi phục vào DATABASE MỚI trống, không chạy đè lên defaultdb hiện có.
Không dùng snapshot SQL làm đường tắt bypass preflight của tool. Phải bật cả hai ứng dụng lại sau khi xong hoặc khi dừng lỗi.

## 4. Kiểm tra sau chuyển

Resume Render, mở trang chủ không query, đối chiếu 4 tên sân.
Mở chi tiết từng sân: ảnh đúng sân, giờ/giá/slot thực tế, booking cũ vẫn thuộc đúng khách và owner.
QR chỉ hiện nếu metadata nguồn có QR; hiện nguồn không có nên không tự thêm.
Đăng nhập chủ sân bằng tài khoản local đã chuyển; không đổi role hoặc tạo mật khẩu mới.
Google email trùng local vẫn xử lý theo chính sách chống liên kết không an toàn hiện có.
Manual Deploy lại rồi kiểm tra ảnh còn nguyên: Render đọc stored_uploads, cache /tmp không phải nguồn dữ liệu.
Nếu sai thì dừng thao tác thêm dữ liệu, giữ backup, không tự xóa/restore đè. Lập kế hoạch rollback/merge riêng.

## Kiểm tra công cụ đã chạy

- javac Java 21 biên dịch thành công.
- Self-test H2 xác nhận thứ tự FK, giữ ID, hash ảnh, chặn target không trống và rollback khi vi phạm FK.
- Prepare chạy thực tế trên MySQL local thành công, có 4 ảnh gốc.
- Aiven: chưa thể kiểm tra/kết nối/chuyển khi chưa có credentials trong tiến trình local của bạn.
