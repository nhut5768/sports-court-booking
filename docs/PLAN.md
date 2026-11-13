# Kế hoạch triển khai — Sân Nhà

Đề án HKDN · Nhóm 2 · Repo: https://github.com/nhut5768/sports-court-booking

Làm trên nhánh `feature/polish-website`. Không push/merge trừ khi nhóm thống nhất.

## Mục tiêu

Website đặt sân thể thao: Java 21, Spring Boot 3.5.16, Maven Wrapper, Spring MVC, Thymeleaf, Bootstrap 5, Spring Data JPA, Flyway, MySQL 8, Spring Security. Một ứng dụng MVC, không React/Node.

## Việc của bạn trên Windows (môi trường)

Agent không chạy được lệnh máy khi bạn bỏ qua Terminal. Tự kiểm tra:

```powershell
java -version
git --version
mysql --version
docker --version
```

Cần **JDK 21** (Temurin). Nếu chưa có:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Đóng/mở lại Terminal, kiểm tra `java -version` ra 21. Đặt `JAVA_HOME` nếu VS Code/Maven không thấy JDK.

Git: `winget install Git.Git`<br>
MySQL 8 (chạy thật): cài MySQL Server 8, nhớ mật khẩu root.<br>
Docker Desktop: tùy chọn, dùng cho Testcontainers và `compose.yaml`.<br>
Maven Wrapper đã có: `.\mvnw.cmd` — không cần cài Maven.

Tạo nhánh (trong thư mục chứa `.git` của repo nhóm):

```powershell
git status
git switch main
git pull --ff-only origin main
git switch -c feature/project-setup
```

Nếu đang đứng ở `sports-court-booking-java` (thư mục cha), mã nguồn nằm trong `sports-court-booking\`. Copy đúng nội dung vào clone GitHub, xem `docs/GITHUB_TEAM.md`.

## Giai đoạn

| # | Giai đoạn | Nội dung | Trạng thái |
|---|-----------|----------|------------|
| 0 | Môi trường | JDK 21, Git, MySQL, Wrapper, Docker | Cần bạn chạy lệnh |
| 1 | Nền tảng | Java 21, Boot 3.5.16, Flyway, cấu hình env, Clock Asia/Ho_Chi_Minh | Trong mã này |
| 2 | Tài khoản | Đăng ký khách, BCrypt, CSRF, không tự nâng OWNER, lệnh tạo chủ sân | Trong mã này |
| 3 | Sân | CRUD sân, ảnh, tiện ích, khu vực, ngừng hoạt động | Trong mã này |
| 4 | Lịch và giá | Khung 60 phút, khóa giờ, giá mặc định + giá theo khung | Trong mã này |
| 5 | Đặt/hủy | Xác nhận giá, chống trùng (transaction + unique), hủy 2 giờ / chủ sân | Trong mã này |
| 6 | Doanh thu | Chỉ COMPLETED, theo ngày và theo sân | Trong mã này |
| 7 | Giao diện | Tiếng Việt, phân trang, flash, empty, confirm, lỗi | Trong mã này |
| 8 | Kiểm thử | Nghiệp vụ, quyền, đồng thời H2; MySQL Testcontainers nếu có Docker | Trong mã này; **chưa chạy trên máy bạn** |
| 9 | Tài liệu | ERD, demo, phân công 6 người, CI Java 21 | Trong mã này |

## Quy tắc trạng thái đơn

Xem chi tiết `docs/SCHEMA.md`.

- `CONFIRMED`: giữ khung giờ (`active_slot_key`), chưa thu tiền — **không** tính doanh thu.
- `CANCELLED`: trả khung (`active_slot_key = NULL`).
- `COMPLETED`: đã hết giờ chơi **và** chủ sân xác nhận đã thu — **mới** tính doanh thu.

## Phân công 6 thành viên (gợi ý)

Xem bảng cập nhật trong `docs/GITHUB_TEAM.md`.
