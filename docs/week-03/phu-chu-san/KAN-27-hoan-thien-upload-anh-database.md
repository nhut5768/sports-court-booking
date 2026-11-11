# KAN-27 – Hoàn thiện kết nối tải lên, lưu và đọc ảnh sân bằng Database

- **Ngày thực hiện:** 10/11/2026
- **Người thực hiện:** Nguyễn Thanh Phú
- **Nhánh làm việc:** `week3/phu-chu-san-doanh-thu-va-anh`
- **Dự án:** Xây dựng website quản lý và đặt sân thể thao trực tuyến – Sân Nhà

## 1. Mục tiêu thực hiện

Hoàn thiện chức năng tải ảnh sân, lưu trữ hình ảnh trực tiếp trong Database và truy xuất ảnh để hiển thị trên giao diện quản lý sân.

Đảm bảo quá trình xử lý hình ảnh được kết nối đồng bộ giữa giao diện người dùng, Backend và Database, giúp chủ sân có thể quản lý hình ảnh sân thuận tiện và ổn định.

## 2. Nội dung công việc

Thực hiện hoàn thiện và kiểm tra các chức năng liên quan đến quản lý hình ảnh sân, bao gồm:

- Tiếp nhận danh sách ảnh do chủ sân tải lên từ giao diện quản lý.
- Kiểm tra định dạng và dung lượng tệp ảnh trước khi lưu trữ.
- Xử lý dữ liệu ảnh và lưu vào Database dưới dạng `LONGBLOB`.
- Lưu thông tin liên kết giữa hình ảnh và sân tương ứng.
- Xây dựng chức năng truy xuất dữ liệu ảnh từ Database.
- Cung cấp Media API để trình duyệt truy cập và hiển thị hình ảnh.
- Hoàn thiện chức năng xóa ảnh sân đã lưu trữ.
- Kiểm tra khả năng duy trì dữ liệu ảnh sau khi khởi động lại ứng dụng.

## 3. Các thành phần mã nguồn liên quan

| File | Chức năng |
|---|---|
| `ImageStorageService.java` | Xử lý lưu trữ và đọc dữ liệu ảnh |
| `MediaController.java` | Cung cấp API đọc ảnh |
| `StoredUpload.java` | Entity lưu dữ liệu ảnh |
| `StoredUploadRepository.java` | Truy vấn dữ liệu ảnh |
| `CourtImage.java` | Entity lưu thông tin ảnh sân |
| `CourtImageRepository.java` | Truy vấn thông tin ảnh sân |
| `BookingService.java` | Xử lý nghiệp vụ thêm và xóa ảnh |
| `OwnerController.java` | Tiếp nhận yêu cầu tải ảnh từ chủ sân |
| `V14__durable_uploads.sql` | Migration tạo bảng lưu trữ dữ liệu ảnh |
| `application-render.properties` | Cấu hình lưu trữ ảnh bằng Database trên Render |

## 4. Quy trình hoạt động của chức năng

Quy trình xử lý ảnh sân được thực hiện theo các bước:

1. Chủ sân lựa chọn và tải ảnh lên từ giao diện quản lý sân.
2. `OwnerController` tiếp nhận yêu cầu tải ảnh.
3. `BookingService` xử lý nghiệp vụ và chuyển dữ liệu ảnh đến `ImageStorageService`.
4. `ImageStorageService` kiểm tra dữ liệu và lưu ảnh vào bảng `stored_uploads`.
5. Thông tin ảnh được lưu trong bảng `court_images` để liên kết với sân tương ứng.
6. Khi giao diện cần hiển thị ảnh, `MediaController` truy xuất dữ liệu từ Database và trả về trình duyệt thông qua Media API.
7. Khi chủ sân thực hiện xóa ảnh, hệ thống xử lý và cập nhật dữ liệu lưu trữ tương ứng.

## 5. Cấu hình và tổ chức lưu trữ Database

Các thành phần lưu trữ được thiết lập như sau:

- **Hệ quản trị Database:** MySQL.
- **Chế độ lưu trữ ảnh trên Render:** Database.
- **Bảng `stored_uploads`:** Lưu nội dung ảnh dưới dạng dữ liệu nhị phân `LONGBLOB`.
- **Bảng `court_images`:** Lưu thông tin và liên kết ảnh với sân.
- **Migration:** `V14__durable_uploads.sql`.
- **Cấu hình liên quan:** `application-render.properties`.

Việc lưu trữ hình ảnh trực tiếp trong Database giúp dữ liệu được quản lý tập trung, duy trì sau khi ứng dụng khởi động lại và hạn chế phụ thuộc vào thư mục lưu trữ cục bộ.

## 6. Kết quả thực hiện và kiểm thử

Sau khi hoàn thiện các thành phần liên quan, tiến hành kiểm tra các chức năng và ghi nhận kết quả:

| STT | Nội dung kiểm thử | Kết quả |
|---|---|---|
| 1 | Tải ảnh sân từ giao diện quản lý | Thành công |
| 2 | Kiểm tra định dạng ảnh tải lên | Thành công |
| 3 | Kiểm tra giới hạn dung lượng ảnh | Thành công |
| 4 | Lưu dữ liệu ảnh vào MySQL | Thành công |
| 5 | Lưu thông tin liên kết ảnh với sân | Thành công |
| 6 | Truy xuất ảnh thông qua Media API | Thành công |
| 7 | Hiển thị ảnh đã tải lên trên giao diện | Thành công |
| 8 | Xóa ảnh sân đã lưu | Thành công |
| 9 | Kiểm tra dữ liệu ảnh sau khi khởi động lại ứng dụng | Thành công |

**Đánh giá:** Các chức năng được giao đã được hoàn thiện và kiểm tra. Quy trình tải ảnh, lưu trữ trong Database, truy xuất và hiển thị ảnh hoạt động tốt. Dữ liệu ảnh được liên kết với sân tương ứng, hỗ trợ chủ sân quản lý hình ảnh thuận tiện và đảm bảo tính ổn định trong quá trình sử dụng.

## 7. Đánh giá chung

Hoàn thành nhiệm vụ KAN-27 – Hoàn thiện kết nối tải lên, lưu và đọc ảnh sân bằng Database.

Trong quá trình thực hiện, đã củng cố kiến thức về Spring Boot, Spring Data JPA, MySQL, Flyway Migration và cách xây dựng API xử lý dữ liệu hình ảnh. Các chức năng được giao đáp ứng yêu cầu, góp phần hoàn thiện chức năng quản lý sân của hệ thống.
