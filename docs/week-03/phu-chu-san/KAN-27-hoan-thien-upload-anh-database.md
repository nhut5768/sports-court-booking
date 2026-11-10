\# KAN-27 – Hoàn thiện kết nối tải lên, lưu và đọc ảnh sân bằng Database



\- \*\*Ngày thực hiện:\*\* 10/11/2026

\- \*\*Người thực hiện:\*\* Nguyễn Thanh Phú

\- \*\*Nhánh làm việc:\*\* `week3/phu-chu-san-doanh-thu-va-anh`

\- \*\*Dự án:\*\* Xây dựng website quản lý và đặt sân thể thao trực tuyến – Sân Nhà



\## 1. Mục tiêu thực hiện



Tìm hiểu, đối chiếu và hoàn thiện các thành phần liên quan đến chức năng tải ảnh sân, lưu trữ ảnh trong Database và đọc ảnh để hiển thị trên giao diện quản lý sân.



Đảm bảo các thành phần giao diện, Backend và Database có sự liên kết trong quy trình quản lý hình ảnh sân thể thao.



\## 2. Nội dung công việc



Trong quá trình thực hiện, tập trung rà soát và đối chiếu các thành phần mã nguồn liên quan đến chức năng quản lý ảnh sân, bao gồm:



\- Tiếp nhận danh sách ảnh do chủ sân tải lên từ giao diện quản lý.

\- Kiểm tra định dạng và dung lượng tệp ảnh trước khi lưu trữ.

\- Xử lý dữ liệu ảnh và lưu vào Database dưới dạng `LONGBLOB`.

\- Lưu thông tin liên kết giữa ảnh và sân tương ứng.

\- Xây dựng chức năng đọc dữ liệu ảnh từ hệ thống lưu trữ.

\- Cung cấp đường dẫn Media API để trình duyệt truy xuất và hiển thị ảnh.

\- Hỗ trợ chức năng xóa ảnh sân đã được lưu trữ.



\## 3. Các thành phần mã nguồn liên quan



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

| `V14\_\_durable\_uploads.sql` | Migration tạo cấu trúc bảng lưu trữ ảnh |

| `application-render.properties` | Cấu hình lưu trữ ảnh bằng Database trên Render |



\## 4. Quy trình hoạt động của chức năng



Quy trình xử lý ảnh sân được tổ chức theo các bước:



1\. Chủ sân lựa chọn và tải ảnh lên từ giao diện quản lý sân.

2\. `OwnerController` tiếp nhận yêu cầu tải ảnh.

3\. `BookingService` xử lý nghiệp vụ và chuyển dữ liệu ảnh đến `ImageStorageService`.

4\. `ImageStorageService` xử lý dữ liệu và lưu ảnh vào bảng `stored\_uploads`.

5\. Thông tin ảnh được lưu trong bảng `court\_images` để liên kết với sân tương ứng.

6\. Khi giao diện cần hiển thị ảnh, `MediaController` truy xuất dữ liệu ảnh và trả về trình duyệt thông qua Media API.



\## 5. Cấu hình và tổ chức lưu trữ Database



Các thành phần lưu trữ được xác định như sau:



\- \*\*Hệ quản trị Database:\*\* MySQL.

\- \*\*Chế độ lưu trữ ảnh trên Render:\*\* Database.

\- \*\*Bảng `stored\_uploads`:\*\* Lưu dữ liệu ảnh.

\- \*\*Bảng `court\_images`:\*\* Lưu thông tin và liên kết ảnh với sân.

\- \*\*Migration:\*\* `V14\_\_durable\_uploads.sql`.

\- \*\*Cấu hình liên quan:\*\* `application-render.properties`.



Việc lưu dữ liệu ảnh trong Database giúp dữ liệu hình ảnh được quản lý tập trung và hạn chế phụ thuộc vào thư mục lưu trữ cục bộ của ứng dụng.



\## 6. Kết quả thực hiện và trạng thái kiểm tra



Sau khi rà soát và đối chiếu mã nguồn giữa repository nguồn và repository `sports-court-booking`, ghi nhận các kết quả sau:



| STT | Nội dung | Kết quả |

|---|---|---|

| 1 | Đối chiếu các file xử lý lưu trữ ảnh | Hoàn thành |

| 2 | Rà soát chức năng tiếp nhận ảnh từ giao diện | Đã có thành phần mã nguồn |

| 3 | Rà soát xử lý lưu ảnh vào Database | Đã có thành phần mã nguồn |

| 4 | Rà soát thông tin liên kết ảnh với sân | Đã có thành phần mã nguồn |

| 5 | Rà soát API đọc và trả dữ liệu ảnh | Đã có thành phần mã nguồn |

| 6 | Rà soát chức năng xóa ảnh | Đã có thành phần mã nguồn |

| 7 | Kiểm tra chức năng trên môi trường chạy thực tế | Chưa xác minh |



\*\*Đánh giá:\*\* Các file mã nguồn liên quan đến chức năng tải lên, lưu trữ và đọc ảnh đã được tìm thấy trong nhánh làm việc. Tuy nhiên, chưa thực hiện xác minh đầy đủ quy trình trên môi trường chạy thực tế nên chưa kết luận chức năng hoạt động hoàn chỉnh.



\## 7. Đánh giá chung



Đã hoàn thành việc rà soát và đối chiếu các thành phần mã nguồn liên quan đến chức năng tải lên, lưu trữ và đọc ảnh sân bằng Database. Qua đó xác định được vai trò của từng thành phần trong quy trình xử lý ảnh giữa giao diện, Backend và Database.



Trong bước tiếp theo, cần tiếp tục kiểm tra chức năng trên môi trường chạy thực tế, xác minh dữ liệu được lưu trong Database và kiểm tra khả năng hiển thị ảnh sau khi tải lên để đảm bảo chức năng hoạt động đúng yêu cầu của dự án.

