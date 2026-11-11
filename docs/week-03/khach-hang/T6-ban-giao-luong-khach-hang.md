# KAN-25 — T6: Bàn giao luồng khách hàng

Lịch công việc: thứ Sáu, 13/11/2026. Thực hiện sớm: 11/11/2026.
Nhánh duy nhất cho công việc cá nhân trong tuần: `week3/nhu-luong-khach-hang`.

## Luồng được bàn giao

| Bước | Thành phần | Kết quả |
| --- | --- | --- |
| Tìm sân | `HomeController`, `CourtRepository`, `home.html` | Kết hợp từ khóa, môn, khu vực, ngày và khoảng giá; giữ lựa chọn khi phân trang |
| Kết quả | `home.html` | Phạm vi kết quả, giá từ, trạng thái trống/hết giờ/chưa chọn ngày, ảnh và đánh giá |
| Chi tiết | `HomeController.detail`, `detail.html` | Lịch thực tế theo ngày; chỉ giờ FREE có thao tác chọn phù hợp quyền |
| Điều hướng | `CustomerNavigation` | Giữ bộ lọc và trang ban đầu xuyên qua chi tiết/xác nhận; đích quay về chỉ là trang chủ |
| Xác nhận | `BookingController.confirm`, `confirm.html` | Kiểm tra quyền, số điện thoại; hiển thị giá, cọc và giá kỳ vọng của form |
| Tạo đơn | `BookingService.book` | Khóa sân, kiểm tra lại lịch và giá, lưu đơn chờ cọc, không tạo đơn nếu giờ/giá không còn hợp lệ |
| Lỗi khi gửi | `BookingController.book` | Quay về đúng sân/ngày, giữ ngữ cảnh tìm kiếm và thông báo lỗi |

Các controller/repository/template được tái sử dụng từ mã đã tích hợp. `CustomerNavigation` là thành phần hỗ trợ mới, không phải bộ luồng đặt sân thứ hai.

## Quy tắc và tương thích

- Ngày hiển thị lịch trống, không tự loại sân hết giờ; giá lọc là `minPrice` của sân, không phải giá của mọi khung giờ.
- Quay lại kết quả phục hồi ngày tìm kiếm ban đầu; đổi ngày tại chi tiết không âm thầm đổi bộ lọc trước đó.
- `expectedPrice` vẫn tùy chọn với client cũ; form xác nhận mới luôn gửi. Giá tính tiền luôn lấy từ database.
- Đơn mới là `PENDING_DEPOSIT`; không đồng nghĩa đã nhận cọc hoặc chuyển khoản thành công.
- Không sửa handler đăng nhập của thành viên khác. Khách chưa đăng nhập vẫn cần đăng nhập rồi chọn lại giờ nếu handler hiện hành không phục hồi yêu cầu đã lưu.

## Kiểm chứng

Checkout GitHub được kiểm tra bằng `mvn -o -Dtest=CourtIntegrationTest test`. Build vẫn dừng ở biên dịch vì thiếu các lớp nền `ReviewService`, `NotFoundException`, `GoogleOidcUserService`. Vì vậy chưa thể tuyên bố checkout này build độc lập hoặc sẵn sàng merge.

Để kiểm chứng thay đổi, dùng bản local đầy đủ trong `target/t56-validation`, chép đè các tệp luồng khách hàng từ nhánh hiện tại và chạy:

```powershell
mvn -o -f target/t56-validation/pom.xml verify
```

Kết quả: **BUILD SUCCESS — 159 tests, 0 failures, 0 errors, 0 skipped**, đóng gói Spring Boot JAR thành công, tổng thời gian 2 phút 29 giây. Riêng `CourtIntegrationTest`: **55 ca đạt**, thời gian 25,38 giây, gồm 51 ca trước đó và 4 ca T5 mới. `git diff --check` đạt.

Đây là kiểm chứng với H2/MockMvc trên mã local đầy đủ, không thay thế build checkout GitHub hoặc kiểm thử trình duyệt/MySQL thật. Không commit thư mục kiểm chứng, log, credentials hay ảnh upload.

## PR và điều kiện merge

Đã tạo [PR #25](https://github.com/nhut5768/sports-court-booking/pull/25) từ nhánh tuần sang `main` ở trạng thái draft vì còn thiếu thành phần nền của nhóm. Chưa merge PR.

Trước khi chuyển PR sang sẵn sàng review/merge:

1. Nhóm tích hợp đủ các lớp nền thiếu và template/asset phụ thuộc, tránh chép đè mã của thành viên khác.
2. Chạy `mvn verify` trực tiếp trên checkout PR, không dùng lớp bổ sung từ local.
3. Kiểm tra trình duyệt: lọc → trang 2 → chi tiết → đổi ngày → xác nhận → quay lại; kiểm tra lỗi giờ/giá thay đổi và số điện thoại còn thiếu.
4. Phong rà soát hợp đồng giờ, giá, trạng thái đơn và cọc theo [bàn giao T4](T4-chi-tiet-lich-xac-nhan.md). Chưa có xác nhận phối hợp thực tế từ Phong.

## Tài liệu liên quan

- [T2 — Kiểm kê kết nối](T2-kiem-ke-ket-noi.md)
- [T3 — Bộ lọc](T3-ket-noi-bo-loc.md)
- [T4 — Chi tiết, lịch và xác nhận](T4-chi-tiet-lich-xac-nhan.md)
- [T5 — Kết quả, giá và phân trang](T5-ket-qua-gia-phan-trang.md)
