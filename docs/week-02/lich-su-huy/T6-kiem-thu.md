# KIỂM THỬ LỊCH SỬ ĐẶT SÂN, ĐIỀU KIỆN HỦY VÀ QUYỀN TRUY CẬP ĐƠN

- Ngày lập tài liệu: 06/11/2026
- Chức năng: KAN-19 – Lịch sử đặt sân và hủy đơn
- Nhánh Git: `week2/min-lich-su-huy`
- Trạng thái: Chưa kiểm thử thực tế

## 1. Mục tiêu kiểm thử

- Kiểm tra chức năng hiển thị lịch sử đặt sân của khách hàng.
- Kiểm tra thông tin chi tiết của từng đơn đặt sân.
- Kiểm tra điều kiện và quy tắc hủy đơn.
- Kiểm tra quyền xem và thao tác đơn giữa khách hàng và chủ sân.
- Đối chiếu các trường hợp kiểm thử với mã nguồn hiện có.

## 2. Kiểm thử lịch sử đặt sân

| Mã | Nội dung kiểm thử | Kết quả mong đợi | Kết quả đối chiếu mã nguồn | Kết quả thực tế |
|---|---|---|---|---|
| LS01 | Khách hàng truy cập trang Lịch của tôi | Hiển thị danh sách đơn của tài khoản đang đăng nhập | Controller có truy vấn theo ID khách hàng đăng nhập | Chưa thực hiện |
| LS02 | Kiểm tra thứ tự danh sách | Đơn mới tạo được hiển thị trước | Đã có sắp xếp theo `CreatedAtDesc` | Chưa thực hiện |
| LS03 | Kiểm tra thông tin đơn | Hiển thị đúng mã đơn, sân, ngày giờ và trạng thái | Chưa đủ mã nguồn để xác minh toàn bộ dữ liệu hiển thị | Chưa thực hiện |
| LS04 | Chọn Chi tiết một đơn | Hiển thị đúng thông tin chi tiết của đơn | Controller có xử lý trang chi tiết và truyền thông tin đơn vào giao diện | Chưa thực hiện |
| LS05 | Tài khoản có trên 10 đơn | Danh sách được phân trang, mỗi trang tối đa 10 đơn | Đã xác nhận `PageRequest.of(Math.max(page, 0), 10)` | Chưa thực hiện |
| LS06 | Tài khoản chưa có đơn | Hiển thị trạng thái danh sách trống phù hợp | Chưa đủ mã nguồn giao diện để xác minh | Chưa thực hiện |

## 3. Kiểm thử điều kiện hủy đơn

| Mã | Nội dung kiểm thử | Kết quả mong đợi | Kết quả đối chiếu mã nguồn | Kết quả thực tế |
|---|---|---|---|---|
| H01 | Khách hủy đơn trước giờ chơi trên 2 tiếng | Hủy thành công nếu đáp ứng điều kiện hủy | Chưa có `BookingService.java` để xác minh logic | Chưa thực hiện |
| H02 | Khách hủy đơn khi còn đúng 2 tiếng | Không cho phép hủy nếu quy tắc yêu cầu hủy trước hơn 2 tiếng | Chưa có mã nguồn xử lý để xác minh | Chưa thực hiện |
| H03 | Khách hủy đơn khi đã đến giờ chơi | Không cho phép hủy | Chưa có mã nguồn xử lý để xác minh | Chưa thực hiện |
| H04 | Khách hủy đơn đã được xác nhận thanh toán cọc | Hủy đơn và xử lý tiền cọc theo chính sách | Chưa xác minh được logic xử lý tiền cọc | Chưa thực hiện |
| H05 | Chủ sân hủy đơn trước giờ chơi | Hủy thành công; tiền cọc được xử lý theo chính sách hoàn cọc | Chưa xác minh được logic hủy và hoàn tiền thực tế | Chưa thực hiện |
| H06 | Chủ sân hủy đơn khi giờ chơi đã bắt đầu | Không cho phép hủy | Chưa có mã nguồn xử lý để xác minh | Chưa thực hiện |
| H07 | Hủy một đơn đã hủy trước đó | Không cho phép hủy lần nữa | Chưa có mã nguồn xử lý để xác minh | Chưa thực hiện |

## 4. Kiểm thử quyền truy cập đơn

| Mã | Nội dung kiểm thử | Kết quả mong đợi | Kết quả đối chiếu mã nguồn | Kết quả thực tế |
|---|---|---|---|---|
| Q01 | Khách truy cập đơn thuộc tài khoản của mình | Được phép xem chi tiết | Controller gọi `requireOwnedBooking(id, viewer)` để kiểm tra quyền sở hữu | Chưa thực hiện |
| Q02 | Khách truy cập đơn của khách hàng khác | Bị từ chối truy cập | Có gọi hàm kiểm tra quyền sở hữu; chưa đủ mã nguồn service để xác minh kết quả từ chối | Chưa thực hiện |
| Q03 | Khách cố gắng hủy đơn của người khác | Bị từ chối thao tác | Controller gọi service hủy; chưa đủ mã nguồn service để xác minh quyền | Chưa thực hiện |
| Q04 | Chủ sân truy cập đơn thuộc sân mình quản lý | Được phép xem đơn theo quyền chủ sân | Chưa đủ mã nguồn để xác minh | Chưa thực hiện |
| Q05 | Chủ sân truy cập đơn thuộc sân của chủ sân khác | Bị từ chối truy cập | Chưa đủ mã nguồn để xác minh | Chưa thực hiện |
| Q06 | Người chưa đăng nhập truy cập chức năng yêu cầu đăng nhập | Được chuyển đến trang đăng nhập hoặc bị từ chối theo cấu hình bảo mật | Chưa có cấu hình Spring Security để xác minh | Chưa thực hiện |

## 5. Đánh giá kết quả

### 5.1. Những nội dung đã đối chiếu được

- Controller có truy vấn lịch sử đặt sân theo ID của khách hàng đang đăng nhập.
- Danh sách lịch sử được sắp xếp theo thời gian tạo giảm dần.
- Mỗi trang lịch sử sử dụng giới hạn 10 đơn.
- Controller có xử lý trang chi tiết đơn và gọi hàm kiểm tra quyền sở hữu đơn.

### 5.2. Những nội dung chưa thể xác minh

- Logic xử lý điều kiện hủy đơn trong `BookingService`.
- Quy tắc xử lý tiền cọc và hoàn tiền.
- Quyền truy cập của chủ sân đối với đơn đặt sân.
- Cấu hình bảo mật đối với người chưa đăng nhập.
- Một số nội dung hiển thị trong giao diện lịch sử và chi tiết đơn.

Nguyên nhân: Bản source code hiện có chưa đầy đủ các lớp xử lý và cấu hình cần thiết để xác minh toàn bộ chức năng.

### 5.3. Quy ước ghi nhận kết quả

- **Đã đối chiếu mã nguồn:** Có đoạn mã liên quan để xác nhận cách xử lý.
- **Chưa đủ mã nguồn để đối chiếu:** Thiếu lớp xử lý hoặc cấu hình liên quan.
- **Chưa thực hiện:** Chưa chạy ứng dụng và chưa thực hiện kiểm thử thực tế.
- Chỉ đánh dấu PASS hoặc FAIL sau khi thực hiện kiểm thử và ghi nhận kết quả thực tế.

## 6. Kết luận

Đã xây dựng 19 trường hợp kiểm thử cho chức năng lịch sử đặt sân, điều kiện hủy đơn và quyền truy cập đơn thuộc KAN-19.

Một số nội dung đã được đối chiếu qua `BookingController.java`. Tuy nhiên, do source code hiện tại chưa đầy đủ và ứng dụng chưa chạy được trong môi trường kiểm thử, chưa thể xác nhận kết quả thực tế của toàn bộ chức năng.

Trạng thái hiện tại: **Đã lập tài liệu và đối chiếu một phần mã nguồn – Chưa kiểm thử thực tế.**