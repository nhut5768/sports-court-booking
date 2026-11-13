# Đưa Sân Nhà lên Internet

Không mua hosting, không trỏ domain, không push/merge trong bước này. File này chỉ chuẩn bị cấu hình và ước lượng chi phí.

## Thành phần cần thuê khi triển khai thật

| Thành phần | Việc dùng | Gợi ý chi phí (VN, tham khảo 2026) |
|---|---|---|
| Máy chủ (VPS) | Chạy JAR Java 21 + Nginx | Khoảng 80.000–250.000 đ/tháng (1–2 GB RAM) |
| MySQL 8 | Lưu tài khoản, sân, đơn | Đi kèm VPS hoặc dịch vụ managed ~100.000 đ/tháng |
| Tên miền | Ví dụ `sannha.vn` hoặc `.com` | ~200.000–400.000 đ/năm |
| HTTPS (Let’s Encrypt) | Chứng chỉ TLS | 0 đ |
| Ổ đĩa ảnh sân | Thư mục `UPLOAD_DIR` bền vững | Đi kèm VPS; không lưu ảnh trong Git |

Chưa tích hợp thanh toán trực tuyến: khách trả tiền tại sân.

## Biến môi trường (không ghi vào Git)

Ứng dụng **không** tự đọc file `.env`. Đặt biến trên máy chủ hoặc trong systemd.

| Biến | Bắt buộc | Ý nghĩa |
|---|---|---|
| `DB_URL` | Có (prod) | JDBC MySQL, ví dụ `jdbc:mysql://127.0.0.1:3306/sports_court_booking?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh` |
| `DB_USERNAME` | Có | User ứng dụng, không dùng root trên Internet |
| `DB_PASSWORD` | Có | Mật khẩu mạnh; chỉ đưa qua môi trường |
| `UPLOAD_DIR` | Có (prod) | Đường dẫn tuyệt đối, ví dụ `/var/lib/san-nha/uploads` |
| `JAVA_HOME` | Có | JDK 21 |

Không in mật khẩu ra log, README hay ticket.

## Lệnh trên máy chủ Linux (khi đã có VPS + MySQL)

```bash
export DB_USERNAME=court_app
export DB_PASSWORD='MAT_KHAU_MYSQL'
export DB_URL='jdbc:mysql://127.0.0.1:3306/sports_court_booking?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh'
export UPLOAD_DIR=/var/lib/san-nha/uploads
java -jar sports-court-booking-1.0.0.jar --spring.profiles.active=prod,mysql
```

Kiểm tra sống: `https://TEN-MIEN/health` phải trả `{"status":"UP"}`.

Đặt Nginx (hoặc Caddy) làm HTTPS, `proxy_pass` tới `http://127.0.0.1:8080`, lưu ảnh ngoài thư mục cài đặt để không mất khi đổi JAR.

## Windows — đóng gói trước khi thuê máy

Trong thư mục chứa `pom.xml`:

```powershell
.\mvnw.cmd clean package
```

File: `target\sports-court-booking-1.0.0.jar`.

Chạy thử production **local không HTTPS** thì **không** bật `prod` (cookie `Secure` sẽ chặn đăng nhập trên http://localhost). Dùng:

```powershell
$env:DB_USERNAME="court_app"
$env:DB_PASSWORD="MAT_KHAU"
.\mvnw.cmd spring-boot:run
```

## MySQL bền vững

1. Chạy `database/setup.sql` một lần (đổi `CHANGE_ME`).
2. Profile `mysql` bật Flyway (`V1__init.sql`).
3. Tắt app rồi bật lại: dữ liệu còn nếu MySQL còn.
4. Profile `demo` (H2 RAM) tách biệt — không dùng trên Internet.

Nếu chưa có MySQL trên máy: cần host, cổng, tên database, username, password. Không đoán mật khẩu và không xóa database có sẵn.

## Ảnh sân

JPEG/PNG/WEBP, tối đa 2 MB/ảnh, 8 ảnh/sân, tên file UUID. Sao lưu thư mục `UPLOAD_DIR` cùng lúc với sao lưu MySQL.
