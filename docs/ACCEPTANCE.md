# Quy tắc nghiệp vụ và nghiệm thu

## Quy tắc MVP
- Giờ theo Asia/Ho_Chi_Minh. Mỗi đơn giữ đúng 1 khung 60 phút, bắt đầu giờ tròn.
- Ngày đặt: hôm nay đến 30 ngày tiếp theo; không đặt khung đã bắt đầu.
- Mỗi sân có một giá/giờ, giờ mở và đóng. Giá đơn không đổi khi chủ sân đổi giá.
- Một khóa slot đang giữ là courtId:date:hour; cột unique nullable active_slot_key được giải phóng khi hủy.
- Giao dịch khóa hàng Court trước khi kiểm tra và sửa lịch. Mọi thao tác đặt/khóa/hủy dùng cùng quy tắc khóa.
- CONFIRMED: đã giữ giờ, chưa ghi nhận thu tiền. CANCELLED: đã hủy, trả giờ. COMPLETED: đã kết thúc và chủ sân xác nhận thu tiền.
- Khách hủy trước giờ bắt đầu trên 2 tiếng. Chủ sân hủy trước giờ bắt đầu. Không hủy đơn hoàn tất.
- Doanh thu lọc theo ngày chơi, chỉ COMPLETED, nhóm theo sân.
- Chỉ chủ của sân được sửa, khóa hoặc xử lý đơn của sân đó. Khách chỉ xem/hủy đơn của mình.

## Test tự động
Chạy `.\mvnw.cmd test` trên Windows hoặc `./mvnw test` trên Linux.
Có kiểm thử giá tại thời điểm đặt, chống trùng (bao gồm 2 luồng), hủy trả giờ, quyền sở hữu, khóa lịch, ngày/giờ hợp lệ, sân ngừng hoạt động, không hoàn tất sớm, CSRF, đăng ký không tự nâng quyền và render các trang Thymeleaf.
Test dùng H2, chưa thay thế kiểm thử MySQL thật.

## Kiểm tra thủ công trên MySQL trước nghiệm thu
1. Tạo chủ sân bằng lệnh README, tạo sân và đăng ký hai khách riêng.
2. Mở hai trình duyệt/phiên riêng, cùng đặt một sân/ngày/giờ. Chỉ một đơn thành công, phiên còn lại báo hết giờ.
3. Khách hủy đơn đủ điều kiện; khách khác đặt lại được.
4. Chủ sân khóa giờ trống; khách không thể đặt. Mở khóa rồi đặt được.
5. Thử dùng tài khoản khách truy cập /owner; phải bị chặn.
6. Tạo chủ sân thứ hai, thử truy cập sửa sân đầu tiên; phải bị chặn.
7. Đổi giá, kiểm tra đơn cũ giữ giá cũ; đơn mới nhận giá mới.
8. Thử hủy đơn đã bắt đầu / hủy sát giờ / hoàn tất trước giờ kết thúc; phải bị chặn.
9. Sau lượt chơi và đã thu tiền, hoàn tất đơn; doanh thu tăng đúng số tiền. Đơn hủy không tăng doanh thu.
10. Dừng/chạy ứng dụng MySQL lại; tài khoản, sân, đơn phải còn.
11. Thử màn hình điện thoại và trường dữ liệu không hợp lệ.
12. Ghi ngày kiểm thử, người thực hiện, kết quả thực tế và ảnh minh chứng vào báo cáo.
