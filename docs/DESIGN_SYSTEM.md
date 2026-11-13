# Sân Nhà — thiết kế nền tảng đặt sân

## Phạm vi
- Giữ Spring Boot + Thymeleaf, Bootstrap local, CSRF, OAuth2, phân quyền và các route hiện có.
- Theme dùng chung: static/css/platform.css, tải sau app.css để giữ component nghiệp vụ ổn định.
- Font hệ thống Segoe UI/Tahoma/Arial hỗ trợ tiếng Việt, không gọi Google Fonts hoặc CDN khi chạy.
- Xanh rừng #123e2e, xanh ngọc #08745c, trắng ngà #f7f8f3, vàng chanh #deed9d.
- Khoảng trắng thoáng, đường viền nhẹ, bo góc 11–26px, chuyển động ngắn và prefers-reduced-motion.

## Các trang
- Trang chủ: hero ảnh lớn và tìm kiếm môn/khu vực/ngày, danh mục ảnh riêng từng môn, thẻ sân lớn,
  hướng dẫn 3 bước, lời mời chủ sân. Không thêm số liệu/khuyến mãi/đánh giá giả.
- Trang kết quả dùng cùng route / và giữ bộ lọc; chọn ngày chuyển đúng lịch sang /courts/{id}.
  Số khung giờ FREE trong ngày lấy từ BookingService.slots(). Danh sách không tự loại sân hết giờ:
  trạng thái được ghi rõ trên thẻ để người dùng chọn giờ/ngày khác.
- Giá thẻ là mức thấp nhất hiện có trong court.minPrice, ghi rõ “Từ”. Giá thực tế từng giờ nằm trong lịch.
- Chi tiết sân: gallery ảnh thật do chủ sân cung cấp, điều hướng nội trang, tiện ích, lịch trống,
  bảng giá và hành động đặt/nhắn chủ sân nổi bật. Không thay logic giữ chỗ hoặc giá.
- Đăng nhập/đăng ký: ảnh minh họa thương hiệu, form 48px, Google 44px ở ngoài/dưới form đăng nhập.
- Lịch đặt, xác nhận, QR, tài khoản, đổi mật khẩu, biên tập sân: chung hệ thống form/card/trạng thái.
- Chủ sân: sidebar xanh rừng, số liệu từ DB, bảng rõ trạng thái, responsive và thao tác giữ nguyên.
- Chat thống nhất khách–chủ sân–AI giữ nguyên quy tắc tiếp quản. Nhãn AI và tên chủ sân khác nhau;
  tông nền tin khác nhau, không giả trạng thái online/đã đọc.

## Hình ảnh và nguồn
Ảnh minh họa mới ở static/images/editorial. Nguồn và giấy phép đầy đủ:
- [SOURCES.md](../src/main/resources/static/images/editorial/SOURCES.md)
- [sources.json](../src/main/resources/static/images/editorial/sources.json)

Ảnh Pexels chỉ dùng hero, danh mục và khu vực thương hiệu, không làm ảnh của sân cụ thể.
Ảnh thật của sân giữ nguyên trong uploads, không bị sửa/xóa. Placeholder rõ ràng nếu chưa có ảnh.
Không hotlink: runtime tải toàn bộ ảnh minh họa từ chính server. Tổng 5 ảnh mới khoảng 810 KiB,
hero 1800px khoảng 277 KiB, ảnh danh mục 840px. Ảnh dưới màn hình đầu dùng lazy loading + async decode.

/media/courts/{id}/{file}?width=480|800|1440 trả JPEG preview nếu giải mã phù hợp, với cache bộ nhớ
32 mục và tối đa 2 tác vụ nén đồng thời. Giữ nguyên ảnh gốc, kiểm tra court/file trước khi trả preview.
Giới hạn ảnh nguồn 40 triệu pixel và chiều không quá 20.000px để tránh xử lý quá lớn.
WebP/ảnh không giải mã được hoặc JPEG có EXIF sẽ trả ảnh gốc để giữ định dạng/hướng ảnh.
Không xử lý lại ảnh QR. Full-resolution gallery vẫn dẫn tới ảnh gốc.

## Trạng thái và khả năng truy cập
- Nhãn input, focus-visible, skip link, heading có thứ bậc, trạng thái có chữ bên cạnh màu.
- Trạng thái đang tìm/đang gửi, lỗi form, hết kết quả, chưa có ảnh, ảnh tải lỗi, QR tải lỗi.
- Ảnh lỗi không bị thay bằng stock của sân khác; giữ nền trung tính và thông báo.
- Không có đoạn HTML do người dùng/AI được chèn trực tiếp; chat tiếp tục dùng textContent.
- Dropdown tài khoản, điều hướng mobile, gallery dialog, sao chép QR và password toggle giữ nguyên.

## Kiểm tra
    .\mvnw.cmd test "-DsanNha.uiSnapshots=true"
    node scripts/ui-smoke.mjs

Kiểm tra các trang chính ở 1440, 1024, 768, 390 và 320px; kiểm tra tràn ngang, label, ID trùng,
anchor lỗi, JavaScript, filter mobile, menu, gallery, sao chép, chống gửi trùng và reduced motion.
CourtIntegrationTest kiểm tra ngày tìm kiếm chuyển vào lịch và số giờ trống từ booking thực tế.
CourtPhotoPreviewTest kiểm tra kích thước bản xem trước, cache, ảnh gốc không đổi và fallback.
Các kiểm thử Google, Gemini, quyền/SSE/AI tiếp quản tiếp tục chạy trong bộ kiểm thử hiện có.

Kết quả hoàn tất: 80 kiểm thử tự động đạt; 110 lượt kiểm tra giao diện (22 bản render trang/trạng thái × 5 kích thước), không tràn ngang/lỗi JavaScript. Đã kiểm tra riêng Google 44px/CSRF/password toggle và chat AI–chủ sân/reconnect/tiếp quản trên theme mới. Một ảnh sân thực tế giảm từ 2.244.903 byte còn 73.262 byte ở bản xem trước, ảnh gốc giữ nguyên.
