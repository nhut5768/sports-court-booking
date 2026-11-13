# Giao diện Sân Nhà

Nhánh: `ui/professional-redesign`. Spring Boot, Thymeleaf và Bootstrap hiện tại được giữ nguyên.

## Cách xem

Với MySQL đã cấu hình theo README hiện tại:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--server.port=8082"
```

Mở http://localhost:8082. Dùng tài khoản hiện có để xem trang khách hàng hoặc chủ sân.
Không cần import lại SQL hoặc chạy migration cho thay đổi giao diện này.

Để xem giao diện mà không kết nối MySQL và không tạo dữ liệu mẫu:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--server.port=8082 --app.demo.seed=false"
```

Profile demo dùng H2 trong RAM; dữ liệu của phiên demo không được giữ khi dừng ứng dụng.
Có thể đăng ký tài khoản khách hàng trên phiên này. Dashboard chủ sân có thể xem bằng
bản render kiểm thử dưới đây hoặc tài khoản chủ sân hiện có trong môi trường MySQL.

## Kiểm tra tự động

```powershell
.\mvnw.cmd test "-DsanNha.uiSnapshots=true" "-Ddebug=false"
node scripts/ui-smoke.mjs
```

Lệnh đầu chạy kiểm thử tích hợp với H2 riêng và xuất HTML Thymeleaf từ fixture kiểm thử.
Lệnh sau cần Node 22+ và Chrome/Edge có sẵn trên Windows; có thể đặt `CHROME_PATH`
để chọn executable khác. Không cần npm install hoặc tải trình duyệt.

Script chỉ phục vụ HTML kiểm thử và tài nguyên local trên địa chỉ loopback tạm thời.
Ảnh sân của fixture được ánh xạ sang ảnh local hiện có để kiểm tra gallery;
không thêm ảnh hoặc số liệu mẫu vào trang production. Tài nguyên mạng ngoài bị chặn.

Kết quả nằm trong `target/ui-preview/browser-report.json` và ảnh PNG desktop/điện thoại.
Các file này, HTML fixture và profile trình duyệt đều nằm trong thư mục `target/` đã được Git bỏ qua.

Kiểm tra bố cục tại 1440, 1024, 768, 390 và 320 px; tràn ngang toàn trang,
ID trùng, nhãn biểu mẫu, liên kết neo, menu, bộ lọc thu gọn, sidebar, gallery,
sao chép, gửi form, chống gửi lặp, hủy xác nhận và reduced motion.
Kiểm thử Spring kiểm tra render, form, phân quyền, CSRF và các nghiệp vụ hiện có.

## Tổ chức giao diện

- `templates/fragments.html`: head, siteHeader, footer, flash, pager và bộ icon SVG nội bộ.
- `static/css/app.css`: token màu/khoảng cách, typography, component, từng nhóm trang và breakpoint.
- `static/vendor/san-nha-ui.js`: JavaScript nội bộ, không phụ thuộc thư viện ngoài.
  Đặt tại đường dẫn static đã được SecurityConfig hiện tại cho phép truy cập công khai.
- Template dùng nguyên model, endpoint, tên trường và biểu thức quyền hiện có.
- Không có biểu đồ hoặc chuỗi dữ liệu giả. Doanh thu dùng nguyên các chỉ tiêu backend.

## Giới hạn của lần kiểm tra

Chrome chạy trên HTML render qua MockMvc và fixture H2, không phải dữ liệu MySQL thật.
Các kiểm tra trình duyệt không thực hiện chuyển khoản hoặc gửi thay đổi đến ứng dụng thật.
QR ngân hàng bên ngoài được chặn trong kiểm tra trình duyệt; đã kiểm tra trạng thái không tải
được QR, còn việc quét QR thực tế bằng ứng dụng ngân hàng cần kiểm tra riêng.
Chưa kiểm tra trên Safari/iOS hoặc thiết bị điện thoại vật lý.