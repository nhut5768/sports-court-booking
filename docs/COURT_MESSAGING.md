# Tin nhắn khách hàng và chủ sân

Khách bấm Nhắn tin chủ sân tại trang sân để mở hội thoại gắn đúng sân và chủ sân trong database. Chủ sân xem/trả lời trong Tin nhắn của trang quản lý. Có thể chọn chủ sân từ danh bạ chat để mở hội thoại trực tiếp.

Mỗi API kiểm tra người tham gia, vai trò và khóa tài khoản; khách chỉ xem hội thoại của mình, chủ sân chỉ xem hội thoại của mình. Khách Google dùng cùng tài khoản CUSTOMER nội bộ. Sender luôn lấy từ phiên đăng nhập.

Tin nhắn lưu database, UUID chống gửi trùng, phân trang lịch sử và số chưa đọc. SSE chỉ gửi thông báo sau commit đến hai người tham gia; giao diện tải bù khi kết nối lại. Các kiểm thử bao gồm SSE qua HTTP, tài khoản ngoài cuộc, rollback và đăng xuất.

Gemini là mục Hỏi AI riêng, không trả lời thay chủ sân. Tham số askAi cũ không kích hoạt AI trong hội thoại người thật. Giữ nguyên lịch sử và schema V12/V13 để tương thích dữ liệu đã có; các tin AI cũ vẫn mang nhãn AI. Không sửa migration đã chạy.
