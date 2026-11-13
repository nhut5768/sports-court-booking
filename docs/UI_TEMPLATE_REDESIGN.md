# Giao diện Tabler cho Sân Nhà

Nhánh: ui/template-redesign. Ngày thực hiện: 04/11/2026. Bản giao diện chia sẻ cho team qua nhánh main.

## Thiết kế và phạm vi

Tabler 1.4.0, giao diện Bootstrap miễn phí theo MIT: https://github.com/tabler/tabler và https://tabler.io/license. CSS và giấy phép được lưu cục bộ trong static/vendor/tabler; thông tin tải xuống, phiên bản và SHA-256 nằm trong README.md tại đó.

Dùng Tabler làm nền Bootstrap và điều chỉnh bằng template-redesign.css: màu xanh thể thao, nền sáng, thẻ sân, biểu mẫu, trạng thái đơn, tài khoản, đăng nhập/đăng ký, thanh quản lý chủ sân và quản trị. Shared head áp dụng theme cho cả trang lỗi. Không dùng JavaScript demo của template.

Trang chủ và tìm sân dùng cùng home.html. Hero lấy ảnh cover của sân đầu tiên trong kết quả; nếu thiếu ảnh dùng nền màu, không gán ảnh minh họa thành ảnh sân thật. Ảnh sân vẫn qua /media/courts; không thêm dữ liệu mẫu vào database. Danh mục lấy số sân từ hệ thống, dùng icon thay ảnh minh họa.

Giữ nguyên controller, đường dẫn, th:field, th:action, sec:authorize, CSRF, các script đặt lịch, QR, thông báo, đánh giá, Google OAuth và chat. Navigation script chỉ đánh dấu đường dẫn hiện tại.

## File sửa

- src/main/resources/templates/account.html
- src/main/resources/templates/admin-account.html
- src/main/resources/templates/admin-accounts.html
- src/main/resources/templates/admin-audit.html
- src/main/resources/templates/booking-detail.html
- src/main/resources/templates/bookings.html
- src/main/resources/templates/change-password.html
- src/main/resources/templates/confirm.html
- src/main/resources/templates/court-form.html
- src/main/resources/templates/detail.html
- src/main/resources/templates/error.html
- src/main/resources/templates/fragments.html
- src/main/resources/templates/home.html
- src/main/resources/templates/login.html
- src/main/resources/templates/messages.html
- src/main/resources/templates/owner.html
- src/main/resources/templates/register-owner.html
- src/main/resources/templates/register.html

## File thêm

- src/main/resources/static/css/template-redesign.css
- src/main/resources/static/vendor/template-navigation.js
- src/main/resources/static/vendor/tabler/tabler.min.css
- src/main/resources/static/vendor/tabler/LICENSE
- src/main/resources/static/vendor/tabler/README.md
- docs/UI_TEMPLATE_REDESIGN.md

## Thay đổi có sẵn được giữ

- AccountService.java: kiểm tra số tài khoản 6–19 chữ số, đã có trước tác vụ UI.
- account.html: phần QR xem trước và bank-qr.js đã có trước tác vụ UI; chỉ thêm lớp trang để nhận theme.
- static/vendor/bank-qr.js: giữ nguyên.
- Không sửa file SQL, không stash/reset, không thay đổi dữ liệu runtime.

## Kiểm tra

- Maven: mvnw.cmd test -DsanNha.uiSnapshots=true — 148 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.
- So sánh 20 template với HEAD: th:action, th:field, sec:authorize không thay đổi.
- node scripts/ui-smoke.mjs — 24 trang, 120 lượt viewport (1440, 1024, 768, 390, 320px), không lỗi kiểm tra và không lỗi JavaScript. Kiểm tra menu mobile, lọc sân, sidebar, gallery, sao chép nội dung cọc, chống gửi form hai lần và reduced motion.
- Đã xem screenshot trang chủ desktop/mobile, chủ sân, đăng nhập, quản trị; sửa độ tương phản footer sau khi xem.
- Báo cáo và screenshot: target/ui-preview/browser-report.json, target/ui-preview/*-1440.png và *-390.png. Log Maven: target/template-redesign-tests.log.

## Giới hạn

Browser smoke dùng HTML Thymeleaf được render từ dữ liệu kiểm thử H2 và ảnh fixture của script có sẵn, không phải toàn bộ database MySQL thật. Chưa thực hiện đăng nhập Google bằng tài khoản người dùng, chuyển khoản thật/quét QR bằng app ngân hàng, gọi Gemini, chat giữa hai trình duyệt thật hoặc kiểm tra trên thiết bị iOS/Android vật lý. Các luồng này được giữ nguyên và có kiểm thử tự động phù hợp; không xem kết quả smoke là xác nhận giao dịch thật.
