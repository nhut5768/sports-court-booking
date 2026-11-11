# BÀN GIAO TÍCH HỢP CHỦ SÂN, HÌNH ẢNH VÀ DOANH THU – TUẦN 3

- **Ngày:** 11/11/2026
- **Thành viên:** Nguyễn Thanh Phú
- **Dự án:** Sân Nhà – Website quản lý và đặt sân thể thao
- **Phạm vi:** Quản lý chủ sân, hình ảnh sân, QR thanh toán, thống kê doanh thu và truy vấn hỗ trợ
- **Nhánh tích hợp:** `week3/phu-chu-san-doanh-thu-va-anh`
- **Pull Request tích hợp:** [PR #29](https://github.com/nhut5768/sports-court-booking/pull/29)
- **Merge commit:** `2d36bec`

## 1. Mục tiêu bàn giao

Tổng hợp các thành phần mã nguồn đã được tích hợp phục vụ nghiệp vụ chủ sân, quản lý hình ảnh và thống kê doanh thu.

Tài liệu cung cấp thông tin về luồng xử lý, các thành phần liên quan, trạng thái tích hợp và những điểm cần lưu ý khi tiếp tục phát triển hoặc kiểm thử hệ thống.

## 2. Liên kết tài khoản chủ sân và quản lý sân

Chức năng quản lý sân được tổ chức theo tài khoản chủ sân đang đăng nhập.

- `OwnerController.java` tiếp nhận yêu cầu quản lý từ chủ sân.
- `AccountService.java` hỗ trợ xác định tài khoản hiện tại.
- `CourtRepository.java` truy xuất danh sách sân thuộc chủ sân.
- `BookingService.java` kiểm tra quyền sở hữu sân trước khi thực hiện các thao tác quản lý.
- `owner.html` hiển thị danh sách sân, đơn đặt, thống kê và các chức năng quản lý.

Tại endpoint `GET /owner`, hệ thống xác định chủ sân hiện tại, lấy danh sách sân thuộc quyền quản lý và chuẩn bị dữ liệu thống kê.

Khi người dùng chọn một sân cụ thể, hệ thống kiểm tra quyền sở hữu thông qua `requireOwner()` trước khi sử dụng sân đó trong bộ lọc.

Các thao tác quản lý liên quan bao gồm tạo hoặc chỉnh sửa sân, quản lý hình ảnh, khóa khung giờ và cập nhật trạng thái đơn đặt sân.

## 3. Quản lý hình ảnh sân và QR thanh toán

### 3.1. Hình ảnh sân

Luồng xử lý:

1. Chủ sân chọn ảnh trong biểu mẫu quản lý sân.
2. `OwnerController` nhận dữ liệu upload.
3. `BookingService` xử lý nghiệp vụ và liên kết ảnh với sân.
4. `ImageStorageService` thực hiện lưu trữ và đọc dữ liệu ảnh.
5. `CourtImage` và `CourtImageRepository` quản lý thông tin ảnh gắn với sân.
6. `MediaController` cung cấp đường dẫn truy xuất ảnh để hiển thị trên giao diện.

Endpoint truy xuất ảnh sân:

`GET /media/courts/{courtId}/{filename}`

Endpoint xóa ảnh sân:

`POST /owner/courts/{id}/images/{imageId}/delete`

### 3.2. QR thanh toán

Giao diện `account.html` hỗ trợ chủ sân quản lý ảnh QR thanh toán và thông tin ngân hàng.

Các thành phần liên quan gồm:

- `AuthController.java`: Tiếp nhận yêu cầu tải lên hoặc xóa QR.
- `AccountService.java`: Xử lý thông tin tài khoản.
- `ImageStorageService.java`: Hỗ trợ lưu trữ và truy xuất ảnh QR.
- `MediaController.java`: Cung cấp đường dẫn đọc ảnh QR.
- `BookingService.java`: Xác định thông tin QR sử dụng trong nghiệp vụ đặt sân.

Endpoint đọc ảnh QR:

`GET /payment-qr/{ownerId}/{filename}`

Hệ thống hỗ trợ sử dụng ảnh QR được tải lên hoặc đường dẫn VietQR được tạo từ thông tin ngân hàng theo logic nghiệp vụ hiện có.

### 3.3. Lưu trữ dữ liệu ảnh

Các thành phần liên quan đến lưu trữ bền vững:

- `StoredUpload.java`
- `StoredUploadRepository.java`
- `V14__durable_uploads.sql`
- `ImageStorageService.java`

Chi tiết quá trình triển khai và kết quả kiểm thử ảnh đã được ghi nhận trong tài liệu:

`docs/week-03/phu-chu-san/KAN-27-hoan-thien-upload-anh-database.md`

## 4. Tổng hợp doanh thu và quản lý tiền cọc

Chức năng thống kê doanh thu được xử lý thông qua:

- `OwnerController.java`
- `BookingService.java`
- `BookingRepository.java`
- `owner.html`

Tại `GET /owner`, Controller nhận các bộ lọc:

- `from`: Ngày bắt đầu.
- `to`: Ngày kết thúc.
- `courtId`: Sân cần thống kê.
- `status`: Trạng thái đơn đặt.
- `page`: Trang dữ liệu đặt sân.

Phương thức `BookingService.revenueReport()` tổng hợp báo cáo doanh thu theo chủ sân, khoảng thời gian và sân được chọn.

Báo cáo phân biệt:

- **Tiền thuê sân:** Giá trị đơn đặt đã hoàn tất (`COMPLETED`).
- **Tiền cọc bị giữ:** Khoản cọc được ghi nhận theo điều kiện hủy và xử lý cọc trong nghiệp vụ.
- **Tiền cọc đang giữ:** Khoản tiền đã nhận nhưng chưa được cộng vào doanh thu ghi nhận.
- **Doanh thu ghi nhận:** Tổng tiền thuê sân hoàn tất và tiền cọc bị giữ được tính vào doanh thu.
- **Doanh thu theo sân:** Tổng hợp số tiền theo từng sân thuộc chủ sân.

Lưu ý: Khoản tiền cọc đang giữ được truy vấn riêng theo trạng thái nghiệp vụ và không áp dụng hoàn toàn cùng phạm vi ngày với phần doanh thu ghi nhận.

## 5. Truy vấn dữ liệu và lịch quản lý

`BookingRepository.java` cung cấp các phương thức hỗ trợ:

| Phương thức | Vai trò |
|---|---|
| `searchForOwner()` | Lọc và phân trang đơn đặt sân |
| `revenue()` | Tổng hợp tiền từ đơn hoàn tất |
| `completedForOwner()` | Truy vấn đơn hoàn tất theo thời gian |
| `moneyForOwner()` | Truy vấn đơn liên quan đến khoản tiền đã nhận |
| `heldDepositsForOwner()` | Truy vấn các khoản cọc đang giữ |
| `countByCourtOwnerIdAndStatusIn()` | Thống kê số đơn theo nhóm trạng thái |

Giao diện `owner.html` có các khu vực quản lý đơn đặt sân, thống kê doanh thu và lịch/khóa giờ.

`OwnerController` cung cấp endpoint quản lý khóa giờ:

`POST /owner/courts/{id}/block`

Chức năng này gọi `BookingService.toggleBlock()` để xử lý cập nhật trạng thái khung giờ theo quyền của chủ sân.

## 6. Các thành phần mã nguồn bàn giao

| Thành phần | Vai trò |
|---|---|
| `Account.java` | Thông tin tài khoản |
| `Court.java` | Thông tin sân và liên kết chủ sân |
| `CourtRepository.java` | Truy vấn dữ liệu sân |
| `OwnerController.java` | Điều phối nghiệp vụ quản lý chủ sân |
| `BookingService.java` | Nghiệp vụ sân, đơn đặt và doanh thu |
| `BookingRepository.java` | Truy vấn đơn đặt và thống kê |
| `ImageStorageService.java` | Lưu trữ và đọc hình ảnh |
| `MediaController.java` | Cung cấp endpoint hình ảnh |
| `owner.html` | Trang quản lý chủ sân |
| `court-form.html` | Biểu mẫu quản lý sân |
| `detail.html` | Trang chi tiết sân |
| `account.html` | Trang tài khoản và QR thanh toán |
| `booking-table.html` | Bảng hiển thị đơn đặt sân |

## 7. Trạng thái tích hợp và Pull Request

Mã nguồn của nhánh:

`week3/phu-chu-san-doanh-thu-va-anh`

đã được tích hợp vào `main` thông qua Pull Request #29 ngày 11/11/2026.

Minh chứng:

- PR: https://github.com/nhut5768/sports-court-booking/pull/29
- Merge commit: `2d36bec`
- Commit KAN-27: `be354a0`

Quá trình rà soát xác nhận các thành phần mã nguồn chính đã hiện diện trong dự án. Các khác biệt bổ sung ở `CourtRepository.java` và `detail.html` được giữ nguyên để bảo toàn chức năng hiện có.

## 8. Kiểm chứng và lưu ý bàn giao

- Đã rà soát các file liên quan đến chủ sân, ảnh và doanh thu.
- Đã đối chiếu nội dung các thành phần chính giữa các phiên bản mã nguồn.
- Đã xác nhận PR #29 được merge vào `main`.
- Tài liệu KAN-27 trước đó có ghi nhận kết quả kiểm thử chức năng upload và lưu ảnh.
- Chưa thực hiện lại kiểm thử tích hợp toàn hệ thống trong đợt bàn giao tài liệu này.
- Cần kiểm tra build, cấu hình môi trường, quyền truy cập, lưu trữ ảnh và dữ liệu doanh thu trên phiên bản triển khai thực tế trước khi nghiệm thu toàn hệ thống.

## 9. Kết luận

Hoàn thành việc tổng hợp và bàn giao thông tin tích hợp các thành phần quản lý chủ sân, hình ảnh và doanh thu.

Các thành phần mã nguồn chính đã được đưa vào nhánh `main` thông qua PR #29. Tài liệu này hỗ trợ thành viên trong nhóm tiếp nhận, kiểm tra và tiếp tục phát triển các chức năng liên quan.
