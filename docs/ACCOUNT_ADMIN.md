# Tài khoản Sân Nhà — bản local

## Chính sách hiện tại

- ADMIN chỉ xem/tìm kiếm/lọc tài khoản, sửa name/email/phone, khóa/mở khóa và xem nhật ký.
- Không có API hoặc giao diện tạo OWNER, đổi role, quản lý sân/đơn/doanh thu cho ADMIN.
  POST /admin/accounts, POST /admin/accounts/{id}/role và trang /admin/accounts/new đã bị bỏ.
- Giữ self-lock guard và database lock bảo vệ admin hoạt động cuối cùng; không có xóa vĩnh viễn.
- /register hoặc /register/customer luôn tạo CUSTOMER.
- /register/owner luôn tạo OWNER. Đây là luồng riêng, không dùng role do client truyền.
- Google lần đầu luôn tạo CUSTOMER. Email trùng account có sẵn không được tự liên kết hoặc đổi role.
- Email/phone duy nhất toàn hệ thống. Gửi role=ADMIN, ownerId hoặc mustChangePassword tùy ý không cấp thêm quyền.
- Owner tự đặt password không bị bắt đổi lần đầu. Đăng ký xong về login; login OWNER vào /owner để tự thêm nhiều sân.
- Mỗi owner chỉ quản lý sân/giá/lịch/booking/hội thoại của chính mình; backend kiểm tra ID và principal.
- Khóa vẫn chặn form login, Google, phiên cũ và SSE. Mở khóa không làm cookie cũ hợp lệ trở lại.
- Audit giữ nguyên lịch sử, kể cả các loại thao tác cũ. Không có endpoint mới để thực hiện lại thao tác đã bỏ.
- Không đổi role, chuyển sân hoặc xóa account/dữ liệu hiện có. Không cần migration mới, giữ schema V15.

## Tạo admin đầu tiên bằng CLI riêng, không có đăng ký ADMIN công khai

    .\scripts\bootstrap-admin.ps1 -Email "email-admin-cua-ban@example.com"

Script hỏi password local MySQL và password admin bằng nhập ẩn, chỉ chạy MySQL local.
Không có password mặc định, không ghi password/hash vào log hoặc Git.
Từ chối nếu đã có ADMIN hoặc email trùng. Đây không phải chức năng tạo owner hoặc thay role.
Nếu execution policy chặn:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\bootstrap-admin.ps1 -Email "email-admin-cua-ban@example.com"

## Chạy local

Đặt DB_USERNAME/DB_PASSWORD an toàn, đảm bảo DB_URL là MySQL local. Không dùng shared/prod/render để thử bản này.

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--server.port=8082 --server.address=127.0.0.1"

- http://localhost:8082/register/customer — khách hàng.
- http://localhost:8082/register/owner — chủ sân, hoặc chọn “Dành cho chủ sân” trên trang chủ/đăng nhập.
- http://localhost:8082/login — Google cho khách hoặc email/password; điều hướng theo role.
- http://localhost:8082/admin — quản trị tài khoản sau khi đăng nhập ADMIN.
- http://localhost:8082/owner — owner tự thêm và quản lý sân của mình.

Không đăng ký lại cùng email để biến CUSTOMER thành OWNER. Phải dùng email mới chưa có trong hệ thống.
Không tự gán các sân đang có cho owner mới; owner mới bắt đầu với danh sách sân trống.

## Kiểm tra

    .\mvnw.cmd clean verify "-DsanNha.uiSnapshots=true"
    node scripts/ui-smoke.mjs

Dừng tiến trình Java dùng JAR trước clean/package trên Windows.
RegistrationFlowsTest kiểm tra role cố định, CSRF, email trùng, không forced-password và hai owner tự đăng ký tạo sân độc lập.
AdminIntegrationTest kiểm tra các API đã bỏ, profile không thể đổi role, lock/phiên/Google và truy cập chéo.
LastAdminConcurrencyTest giữ admin cuối khi hai admin khóa nhau đồng thời.
GoogleLoginIntegrationTest giữ first-login CUSTOMER và email-conflict policy.
Các account trong test là fixture H2, không được thêm vào MySQL thật.

Bản này chỉ chạy local, không push hoặc deploy Render/Aiven. Backup local trước đó vẫn nằm trong backups/ bị Git bỏ qua.

Kết quả: clean verify 137 tests đạt; đã kiểm tra JAR không còn OwnerAccountCommand/admin-create.html. Luồng đăng ký còn được chạy lại sau chỉnh giao diện; không thêm account hoặc sân vào MySQL thật trong kiểm thử.
