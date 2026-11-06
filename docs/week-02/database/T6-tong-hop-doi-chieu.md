# T6 tuần 2 — Tổng hợp kết quả đối chiếu entity và SQL

- Người thực hiện: Nguyễn Thanh Thành Nhựt.
- Jira: KAN-16.
- Ngày: 06/11/2026.
- Nhiệm vụ: Tổng hợp kết quả đối chiếu; sửa sai lệch nếu có, không sửa migration đã chạy.

## 1. Phạm vi thực hiện

Tổng hợp kết quả rà soát 13 entity và schema cuối cùng hình thành
từ migration V1–V15 của dự án san-nha-team.

Căn cứ T5: commit 1d76d55da1b77a827bb8b9513da233ec3eb575d2,
bổ sung file database/checks/KAN-16_T5_kiem_tra_entity_sql.sql.

Kết quả dưới đây dựa trên mã nguồn đã đối chiếu.
Chưa có đầu ra thực thi file kiểm tra T5 trên MySQL để kết luận
schema thực tế hoàn toàn trùng khớp.

## 2. Kết quả tổng hợp

| Nội dung | Kết quả rà soát mã nguồn |
|---|---|
| Entity và bảng | Đối chiếu 13 entity với 13 bảng còn tồn tại sau V15. |
| Khóa ngoại | Tổng hợp 18 FK; các quan hệ bắt buộc và tùy chọn phù hợp tính nullable của schema cuối. |
| Kiểu số | Long/long tương ứng BIGINT; int tương ứng INT. |
| Tiền | BigDecimal precision=12, scale=0 phù hợp DECIMAL(12,0). |
| Ngày và thời gian | LocalDate tương ứng DATE; LocalDateTime tương ứng DATETIME/DATETIME(6), cần lưu ý độ chính xác phần lẻ giây. |
| Chuỗi và dữ liệu ảnh | Độ dài chuỗi cơ bản tương ứng; ai_links dùng TEXT; stored_uploads.content dùng LONGBLOB. |
| UNIQUE | Đã rà soát email, phone, google_sub, tên ảnh, khóa khung giờ, review và các khóa chat. |
| CHECK | reviews.rating có CHECK từ 1 đến 5 trong SQL. |
| Schema cuối | phone nullable sau V4; google_sub thêm lại ở V11; court_id/sender_id của chat nullable sau V13; quản trị bổ sung ở V15. |

## 3. Các điểm cần kiểm chứng tiếp

- Naming strategy thực tế phải ánh xạ đúng camelCase sang snake_case.
- Các bảng dùng DATETIME và DATETIME(6) khác độ chính xác thời gian.
- Migration chưa có CHECK cho giờ hợp lệ, giá dương và tập giá trị role/status.
  Cần đối chiếu validation/service trước khi kết luận nghiệp vụ bị thiếu bảo vệ.
- UNIQUE trên bookings và blocked_slots không tự bảo vệ xung đột giữa hai bảng.
  Cần kiểm tra transaction và tình huống đồng thời ở service.
- ai_reply_to có UNIQUE nhưng chưa có FK; các con trỏ đọc chat chưa có FK.
  Cần xác định chủ đích thiết kế trước khi thay đổi.
- Khóa ghép chứa cột nullable không tự loại bỏ mọi trường hợp gửi lặp
  hoặc trùng hội thoại; cần kiểm tra cách tạo khóa ở ứng dụng.
- FK riêng của review không tự bảo đảm court/customer trùng với booking.
- FK tới accounts không tự kiểm tra vai trò OWNER/CUSTOMER.

## 4. Xử lý sai lệch

Qua phạm vi mã nguồn đã cung cấp, chưa phát hiện chênh lệch rõ ràng
về kiểu dữ liệu cơ bản hoặc độ dài cột cần sửa ngay.

Các điểm ở mục 3 là nội dung cần xác minh tiếp, chưa đủ căn cứ
để tự động sửa entity hoặc schema.

Không chỉnh sửa migration V1–V15 đã chạy.
Nếu xác nhận có sai lệch cần thay đổi database, tạo migration mới
sau V15, thống nhất với nhóm và kiểm tra dữ liệu hiện hữu trước.

## 5. Kết quả bàn giao

- Hoàn thành tổng hợp kết quả đối chiếu entity–SQL.
- Ghi rõ kết quả rà soát và các nội dung chưa được kiểm chứng.
- Không thay đổi dữ liệu hoặc migration đã chạy trong nhiệm vụ T6.
- Tiếp tục xác minh MySQL thực tế bằng file truy vấn T5.
- Bổ sung ảnh GitHub, Jira và kết quả thực thi vào báo cáo khi có minh chứng.
