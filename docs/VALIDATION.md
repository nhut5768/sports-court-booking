# Kết quả kiểm tra bản bàn giao

- Java 21, Spring Boot 3.5.16, Maven Wrapper.
- Maven verify: đạt; 14 kiểm thử, không lỗi, không bỏ qua.
- Đóng gói Spring Boot JAR: đạt.
- Chạy JAR chế độ demo và kiểm tra HTTP thực tế: đăng nhập bằng hai tài khoản mẫu, xem sân/lịch, đặt sân, hủy sân, xem trang chủ sân và tải CSS cục bộ: đạt.
- Chống đặt trùng bằng hai luồng: đạt trong kiểm thử tích hợp H2.
- Chưa chạy kiểm thử trên MySQL thật trong môi trường bàn giao; có cấu hình MySQL và hướng dẫn nghiệm thu riêng.
- Chưa kiểm tra trực quan bằng trình duyệt tự động; template được render qua MockMvc và HTTP.
- Chưa đẩy mã nguồn hoặc thay đổi cấu hình repo GitHub của nhóm.
