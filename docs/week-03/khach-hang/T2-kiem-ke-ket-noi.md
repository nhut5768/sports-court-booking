# T2 — Kiểm kê kết nối trang chủ với chi tiết sân

Ngày kiểm kê: 09/11/2026.

## Phạm vi

Kiểm kê luồng khách hàng từ danh sách sân tại `/` đến chi tiết `/courts/{id}`. Tái sử dụng controller, repository và template đã tích hợp; không sao chép thành bộ mã mới.

## Các tệp liên quan

| Tệp | Vai trò và tình trạng hiện tại |
| --- | --- |
| `src/main/java/vn/uth/court/HomeController.java` | Có GET `/` để tải danh sách và GET `/courts/{id}` để tải chi tiết. Truyền `selectedDate` cho trang chủ; chi tiết nhận `date`, mặc định hôm nay khi không có ngày. |
| `src/main/resources/templates/home.html` | Ảnh, tên sân và nút “Xem lịch” đã dẫn đến `/courts/{id}` kèm ngày đã chọn. |
| `src/main/resources/templates/detail.html` | Hiển thị sân và lịch theo ngày; có liên kết quay lại `/`. |
| `src/main/java/vn/uth/court/CourtRepository.java` | Nguồn truy vấn danh sách sân dùng lại qua `HomeController`; không tạo repository trùng. |
| `src/main/java/vn/uth/court/BookingService.java` | `loadDetails` và `slots` cung cấp thông tin sân, lịch cho trang chi tiết. |
| `src/test/java/vn/uth/court/CourtIntegrationTest.java` | Có `discoveryDateUsesRealAvailabilityAndKeepsSelectedDate` và `publicPagesRender` kiểm tra liên kết theo ngày, trang danh sách, trang chi tiết và sân không tồn tại. |

## Kết quả kiểm kê và phần cần bổ sung

- Kết nối cơ bản trang chủ → chi tiết sân đã có; không cần chép lại controller, repository hoặc template tuần 2.
- Ngày được truyền từ thẻ sân sang chi tiết. Liên kết quay lại hiện chỉ về `/`, chưa giữ bộ lọc và trang kết quả trước đó. Cần thống nhất cách giữ ngữ cảnh với các nhiệm vụ T3 và T5 trước khi bổ sung.
- Kiểm thử hiện có kiểm tra đường dẫn chứa ngày và render trang chi tiết riêng; chưa chứng minh thao tác nhấp liên kết trên trình duyệt.

## Kiểm tra trước bàn giao

```powershell
.\mvnw.cmd "-Dtest=CourtIntegrationTest#discoveryDateUsesRealAvailabilityAndKeepsSelectedDate+publicPagesRender" test
```

Chưa chạy lại kiểm thử trong lần kiểm kê ngày 09/11/2026. Không dùng kết quả ngày 06/11 làm kết quả kiểm thử mới.

## Phạm vi commit đề xuất

Chỉ bổ sung bản kiểm kê này cho công việc T2 hiện tại trên nhánh `week3/nhu-luong-khach-hang`, tạo từ `main` của repo `nhut5768/sports-court-booking`. Đã đối chiếu các kết nối trên mã nguồn `main`. Các tệp mã nguồn liệt kê ở trên là thành phần tích hợp cần tham chiếu, không phải toàn bộ mã mới để đưa lại vào commit.
