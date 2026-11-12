# Website đặt sân thể thao trực tuyến

HKDN – Nhóm 2: Website đặt sân thể thao trực tuyến

## 1. Thông tin dự án

- Học phần: HKDN
- Nhóm: 2
- Số thành viên: 6

## 2. Mục tiêu

Hỗ trợ khách hàng tìm kiếm sân, xem lịch trống và đặt sân trực tuyến.
Hỗ trợ chủ sân quản lý lịch đặt, giá thuê và thông tin khách hàng, hạn chế đặt sân trùng giờ.

## 3. Chức năng chính

- Xem và tìm kiếm sân.
- Xem lịch trống.
- Đặt và hủy sân.
- Quản lý sân, khung giờ và giá thuê.
- Quản lý đơn đặt và thông tin khách hàng liên quan.
- Thống kê doanh thu.
- Đăng nhập bằng email hoặc Google.
- Quản lý tài khoản và phân quyền.
- Quản lý tài khoản bị khóa và lịch sử quản trị.
- Chat giữa khách hàng và chủ sân.

## 4. Thành viên

1. Lê Bá Hoài Phong
2. Nguyễn Thanh Thành Nhựt
3. Trần Minh Khang
4. Nguyễn Thanh Phú
5. Bùi Đặng Quỳnh Như
6. Lê Chi Min

## 5. Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21 + Spring Boot |
| Giao diện | Thymeleaf + Bootstrap |
| Truy cập dữ liệu | Spring Data JPA |
| Cơ sở dữ liệu | MySQL |
| Quản lý thư viện | Maven |
| Xác thực | Spring Security, Google OAuth |

## 6. Hướng dẫn chạy dự án local

### Yêu cầu

- Git
- JDK 21
- MySQL 8.4 trở lên

### Cấu hình cơ sở dữ liệu

1. Mở MySQL Workbench.
2. Chạy file `database/setup-local.sql`.
3. Tạo tài khoản MySQL và cấp quyền theo hướng dẫn trong file.
4. Flyway sẽ tự động tạo các bảng khi ứng dụng khởi động.

### Khởi chạy

Tại thư mục dự án, chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1
```

Sau khi khởi chạy, truy cập:

http://localhost:8082

Thông tin kết nối MySQL được nhập theo hướng dẫn của script. Google và Gemini là các cấu hình tùy chọn; không cần nhập nếu chưa sử dụng.

### Các luồng đăng ký

- Khách hàng: `/register/customer`
- Chủ sân: `/register/owner`
- Quản trị viên: tạo tài khoản bằng công cụ bootstrap local, không đăng ký công khai.

## 7. Quản lý tài khoản và phân quyền

- CUSTOMER: tìm kiếm sân, đặt sân và quản lý thông tin cá nhân.
- OWNER: quản lý sân, giá thuê, lịch đặt và thông tin liên quan đến sân của mình.
- ADMIN: quản lý tài khoản, khóa/mở khóa tài khoản và xem lịch sử quản trị.

Việc phân quyền được kiểm tra ở phía máy chủ. Không sử dụng dữ liệu role do trình duyệt gửi lên để cấp quyền.

Tài liệu chi tiết:

- [Quản trị tài khoản](docs/ACCOUNT_ADMIN.md)
- [Cấu hình Google OAuth](docs/GOOGLE_OAUTH.md)
- [Chạy dự án local](docs/RUN_LOCAL.md)

## 8. Tài liệu dự án

- [Hướng dẫn MySQL, backup và khôi phục](database/README.md)
- [Quy trình Git cho thành viên](docs/GITHUB_TEAM.md)
- [Gemini AI](docs/AI_ASSISTANT.md)
- [Chat khách hàng và chủ sân](docs/COURT_MESSAGING.md)
- [Thiết kế và nguồn ảnh](docs/DESIGN_SYSTEM.md)
- [Nguồn ảnh minh họa và giấy phép](src/main/resources/static/images/editorial/SOURCES.md)

## 9. Kiểm tra dự án

Các lệnh kiểm tra được cấu hình trong dự án:

```powershell
.\mvnw.cmd test
.\mvnw.cmd test "-DsanNha.uiSnapshots=true"
node scripts/ui-smoke.mjs
```

## 10. Quản lý công việc

- Jira: https://hkdnnhom2.atlassian.net/jira/software/projects/KAN/boards/2/backlog
- Báo cáo Overleaf: bổ sung sau.

## 11. Quy trình làm việc Git

- Mỗi công việc phát triển trên một nhánh riêng.
- Commit cần có nội dung mô tả rõ ràng.
- Tạo Pull Request khi hoàn thành.
- Có thành viên kiểm tra trước khi merge vào `main`.

## 12. Lưu ý bảo mật

- Không commit file `.env`, API key, mật khẩu, hash mật khẩu hoặc thông tin đăng nhập.
- Không đưa database backup, ảnh upload hoặc QR cá nhân lên Git.
- Mỗi thành viên sử dụng database và thông tin kết nối riêng.
- Không chia sẻ credentials qua Git hoặc tài liệu công khai.
