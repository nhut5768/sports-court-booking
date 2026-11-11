# KAN-25 — T4: Chi tiết sân, lịch trống và xác nhận đặt

Ngày thực hiện: 11/11/2026. Nhánh: `week3/nhu-luong-khach-hang`.

## Luồng khách hàng

1. Từ kết quả tìm kiếm, mở `/courts/{id}?date=...`; dùng `HomeController` và `BookingService.slots` hiện có để hiển thị ngày, giá và trạng thái từng giờ.
2. Khách hàng đã đăng nhập chọn khung `FREE` để mở `/bookings/confirm?courtId=...&date=...&hour=...`. Giờ đã đặt, bị khóa, sân ngừng hoạt động hoặc ngoài thời gian cho phép không được xác nhận.
3. Trang xác nhận hiển thị sân, ngày, giờ, tổng tiền, cọc và phần còn lại. Trang này chưa giữ giờ. Chỉ tài khoản CUSTOMER được mở xác nhận; nếu thiếu số điện thoại, trang hướng dẫn cập nhật tài khoản và khóa nút gửi.
4. Form POST `/bookings` gửi `courtId`, `date`, `hour`, `expectedPrice` và CSRF. Giá gửi lên chỉ để so sánh, không được dùng làm giá tính tiền.
5. Service khóa hàng sân theo cơ chế đã tích hợp, kiểm tra lại giờ và tính giá thực tế. Nếu giá khác giá đã hiển thị, từ chối tạo đơn và trả về lịch đúng sân/ngày kèm thông báo chọn lại. Nếu giờ vừa bị đặt hoặc khóa cũng không tạo đơn trùng.
6. Thành công tạo một đơn `PENDING_DEPOSIT`, lưu giá thực tế và tiền cọc rồi chuyển đến `/bookings/{id}`. Khách tiếp tục cam kết đến sân, báo chuyển cọc và chờ chủ sân xác nhận theo luồng sẵn có; tạo đơn chưa đồng nghĩa đã nhận cọc.

## Phạm vi thay đổi

Ghi chú cuối tuần: [T5](T5-ket-qua-gia-phan-trang.md) bổ sung `returnTo` vào form và liên kết xác nhận để phục hồi bộ lọc/trang ban đầu; quy tắc giá và trạng thái đơn T4 được giữ nguyên.

| Tệp | Thay đổi |
| --- | --- |
| `BookingController.java` | Kiểm tra vai trò ở trang xác nhận; lấy giá một lần; cung cấp trạng thái thiếu điện thoại; chuyển giá đã xem vào service khi POST |
| `BookingService.java` | Thêm overload nhận `expectedPrice`, đối chiếu giá sau khi khóa sân; dùng giá thực tế cho đơn; chặn điện thoại rỗng |
| `confirm.html` | Gửi giá đã xem, hướng dẫn bổ sung điện thoại và vô hiệu hóa nút khi thiếu thông tin |
| `CourtIntegrationTest.java` | Thêm 4 kiểm thử luồng HTTP và dữ liệu đơn |

Tái sử dụng controller, repository, template, khóa giao dịch và quy tắc cọc tuần 2; không tạo bộ chức năng trùng. Chữ ký service bốn tham số vẫn dùng được, gọi overload với giá kỳ vọng null. `expectedPrice` trên POST là tùy chọn để tương thích các lời gọi cũ; form xác nhận mới luôn gửi trường này. Nếu lời gọi cũ không gửi giá kỳ vọng, service vẫn tính giá từ database nhưng không có đối chiếu giá đã xem.

## Kiểm thử bổ sung

- `customerCanFollowDetailConfirmAndCreateBookingWithQuotedPeakPrice`: đi theo liên kết thực từ chi tiết đến xác nhận rồi POST tạo đơn, kiểm tra giá cao điểm 180.000 đồng, cọc 90.000 đồng, trạng thái chờ cọc và giờ chuyển sang BOOKED.
- `bookingRejectsPriceChangedAfterConfirmationWithoutCreatingAnOrder`: giá đổi sau khi xem xác nhận, POST bị từ chối, không có đơn mới và trang xác nhận lần sau hiển thị giá mới.
- `staleConfirmationCannotBookNewlyBlockedOrTakenSlot`: giờ bị khóa hoặc người khác đặt trong lúc khách đang xem xác nhận, không tạo đơn trùng.
- `confirmationRequiresCustomerAndExplainsMissingPhoneBeforeSubmit`: khách chưa đăng nhập được chuyển đến đăng nhập; chủ sân không mở xác nhận; thiếu điện thoại có hướng dẫn, nút gửi bị khóa và POST trực tiếp vẫn bị từ chối.

## Điểm bàn giao để phối hợp Phong

- Giữ nguyên hợp đồng lịch gồm `hour`, `label`, `state`, `price`, cùng các trạng thái FREE/BOOKED/BLOCKED/CLOSED/PAST của service.
- Form xác nhận thêm `expectedPrice`; khi thay đổi phần đặt sân, giữ việc đối chiếu giá trong cùng giao dịch khóa sân và giữ giá đơn đã tạo.
- Giữ nguyên quy tắc tạo đơn chờ cọc, thời hạn giữ giờ, xác nhận chuyển cọc và quyền chủ sân hiện có.
- Cần thống nhất trước khi bắt buộc `expectedPrice` cho mọi client, vì hiện các lời gọi cũ không gửi giá này vẫn được hỗ trợ.
- Đây là ghi chú bàn giao kỹ thuật; chưa xác nhận đã trao đổi hoặc được Phong duyệt.

## Xác minh

Lệnh trên checkout GitHub:

```powershell
mvn -o -Dtest=CourtIntegrationTest test
```

Checkout đã cập nhật main trước khi làm T4 nhưng vẫn thiếu `ReviewService`, `NotFoundException`, `GoogleOidcUserService`, nên build dừng ở biên dịch. Xem `target/t4-checkout.log` trên máy làm việc; không đưa log và target vào Git.

Kiểm chứng riêng thay đổi trên bản sao local đầy đủ trong `target/t4-validation`, chép đè bảy tệp luồng khách hàng T3/T4 từ nhánh hiện tại:

```powershell
mvn -o -f target/t4-validation/pom.xml -Dtest=CourtIntegrationTest test
```

Kết quả kiểm chứng: **BUILD SUCCESS — 51 tests, 0 failures, 0 errors, 0 skipped**, thời gian kiểm thử 28,62 giây; gồm 47 ca đã có sau T3 và 4 ca mới T4. Kiểm thử khách chưa đăng nhập gửi `Accept: text/html` để kiểm tra đúng yêu cầu trang HTML từ trình duyệt.

Môi trường H2/MockMvc; chưa kiểm tra thao tác bằng trình duyệt, MySQL thật hoặc chuyển khoản thật. Các lớp nền trong bản sao chỉ dùng để kiểm chứng, không nằm trong commit. Kết quả bản sao không xác nhận checkout GitHub build độc lập thành công. `git diff --check` đạt.
