# Báo cáo thứ Tư – Rà soát migration V1–V15

**Người thực hiện** Nguyễn Thanh Thành Nhựt  
**Công việc Jira** KAN-16 – Tích hợp entity và migration cơ sở dữ liệu  
**Nguồn rà soát** `san-nha-team/src/main/resources/db/migration`

| Phiên bản | File SQL | Vai trò |
|---|---|---|
| V1 | `V1__init.sql` | Khởi tạo các bảng tài khoản, sân, bảng giá, ảnh sân, đơn đặt và khung giờ bị khóa. Ràng buộc duy nhất trên `bookings.active_slot_key` ngăn hai đơn cùng giữ một khung giờ. |
| V2 | `V2__deposit.sql` | Thêm thông tin ngân hàng và các trường theo dõi số tiền cọc, thời điểm cam kết, báo chuyển, xác nhận và trạng thái mất cọc. |
| V3 | `V3__notifications.sql` | Thêm thông tin ảnh QR nhận cọc. Tạo bảng `notifications` để lưu thông báo theo tài khoản, đơn liên quan và thời điểm đã đọc. |
| V4 | `V4__phone_login_google_reset.sql` | Cho phép số điện thoại để trống, đưa số sai định dạng về `NULL` và giữ số trùng cho tài khoản có ID nhỏ nhất. Thêm ràng buộc duy nhất cho số điện thoại và định danh Google, đồng thời tạo bảng lưu mã đặt lại mật khẩu dưới dạng giá trị băm. |
| V5 | `V5__reset_token_varchar.sql` | Đổi `password_reset_tokens.token_hash` từ `CHAR(64)` sang `VARCHAR(64)` để khớp kiểu dữ liệu Hibernate kiểm tra. |
| V6 | `V6__drop_google_login.sql` | Xóa cột `google_sub` và chỉ mục duy nhất tương ứng khỏi `accounts`. |
| V7 | `V7__reviews.sql` | Tạo bảng đánh giá liên kết với đơn, sân và khách hàng. Mỗi đơn có tối đa một đánh giá, với điểm từ 1 đến 5 sao. |
| V8 | `V8__otp_attempts.sql` | Xóa các bản ghi đặt lại mật khẩu hiện có và thêm cột `attempts` để đếm số lần nhập sai mã. |
| V9 | `V9__drop_password_reset.sql` | Xóa bảng `password_reset_tokens` cùng dữ liệu còn lại. |
| V10 | `V10__hold_reset_reply.sql` | Thêm hạn giữ chỗ và đặt hạn cho các đơn đang chờ cọc thành 30 phút kể từ lúc chạy migration. Thêm cờ buộc đổi mật khẩu tạm và các cột lưu phản hồi của chủ sân. |
| V11 | `V11__google_oidc.sql` | Thêm lại định danh Google duy nhất, phân biệt hoa thường. Tài khoản cũ vẫn chưa liên kết Google. |
| V12 | `V12__court_customer_chat.sql` | Tạo bảng hội thoại và tin nhắn giữa khách hàng với chủ sân. Lưu mốc đã đọc và dùng `client_id` để ngăn ghi trùng khi gửi lại cùng tin nhắn. |
| V13 | `V13__unified_owner_ai_chat.sql` | Hỗ trợ hội thoại trực tiếp với chủ sân, bổ sung trạng thái AI và thông tin phản hồi. Ràng buộc `ai_reply_to` giới hạn mỗi tin được tham chiếu có tối đa một phản hồi AI. |
| V14 | `V14__durable_uploads.sql` | Tạo bảng `stored_uploads` để lưu nội dung nhị phân của tệp tải lên trong MySQL theo khóa `object_key`. |
| V15 | `V15__account_administration.sql` | Thêm trạng thái khóa và phiên bản bảo mật của tài khoản. Tạo bảng điều phối quản trị và bảng nhật ký ghi người thao tác, tài khoản bị tác động, hành động và thời điểm. |

**Kết quả bàn giao** Bảng rà soát đủ 15 migration. Định danh Google được thêm ở V4, gỡ ở V6 và thêm lại ở V11. Bảng mã đặt lại mật khẩu được sửa ở V5 và V8 rồi xóa ở V9. V10 bổ sung cờ buộc đổi mật khẩu cho quy trình cấp mật khẩu tạm.
