# Google OAuth2

Áp dụng cho mọi profile: mysql (mặc định), demo, prod,mysql.
Đọc GOOGLE_CLIENT_ID và GOOGLE_CLIENT_SECRET từ môi trường; thiếu một biến thì giữ đăng nhập cũ,
Google chưa khả dụng. Không có secret trong mã nguồn.

## Google Cloud
1. Mở https://console.cloud.google.com/, tạo/chọn project.
2. Google Auth Platform: cấu hình Branding, Audience và Data Access (openid, email, profile).
   Nếu External ở chế độ Testing, thêm tài khoản Google thật vào Test users.
3. Clients: tạo OAuth client loại Web application.
4. Theo lệnh chạy README (cổng 8082), Authorized JavaScript origins: http://localhost:8082
   và Authorized redirect URIs: http://localhost:8082/login/oauth2/code/google
5. Đặt trong cùng PowerShell đã cấu hình DB_USERNAME/DB_PASSWORD:

    $env:GOOGLE_CLIENT_ID = "CLIENT_ID_TU_GOOGLE"
    $googleSecret = Read-Host "Google Client secret" -AsSecureString
    $env:GOOGLE_CLIENT_SECRET = [System.Net.NetworkCredential]::new("", $googleSecret).Password
    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--server.port=8082"

Mở http://localhost:8082/login → Đăng nhập bằng Google → chọn tài khoản → xác thực → trang chủ.
Không cần đăng ký hoặc tạo mật khẩu riêng. Flyway chạy V11 trên MySQL.
Profile demo dùng H2 trong RAM nên tài khoản mất khi dừng ứng dụng.

Properties chưa đặt server.port. Chạy không truyền cổng mặc định 8080, cần đăng ký
http://localhost:8080/login/oauth2/code/google. Nếu đổi cổng 8083, đăng ký URI cổng 8083 tương ứng.
Callback dùng {baseUrl} nên lấy cổng request thực tế. Không có tiến trình website chạy lúc kiểm tra.
Triển khai thật: https://TEN_MIEN/login/oauth2/code/google và origin tương ứng.
Profile prod dùng HTTPS cookie; chỉ nhận forwarded headers từ reverse proxy tin cậy.

## Chính sách
Lần đầu tạo CUSTOMER, lưu google_sub duy nhất, email đã xác minh và tên. Lần sau tìm bằng sub,
giữ email local. Email trùng tài khoản có sẵn: từ chối, yêu cầu đăng nhập cách cũ, không tự liên kết.
Mật khẩu Google mới không thể xác thực BCrypt; không cấp mật khẩu tạm qua chủ sân.
Quyền lấy từ database. Spring Security OIDC xử lý state, nonce và kiểm tra token.
Lỗi/hủy xác thực quay về login, không hiển thị token hoặc secret.

## Kiểm tra
Chạy .\mvnw.cmd test. Kiểm thử dùng danh tính OIDC giả không gọi Google.
Đăng nhập Google thật cần credentials hợp lệ và thao tác chọn tài khoản trong trình duyệt.
Không lưu secret vào Git/properties/log.

Tham khảo:
https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html
https://developers.google.com/identity/openid-connect/openid-connect
