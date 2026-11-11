# KAN-23 — T6 tuần 3: Kiểm kê mã nguồn và bàn giao

Người thực hiện: Nguyễn Thanh Thành Nhựt
Thời điểm kiểm kê thực tế: 2026-11-11 08:45:28 +07:00
Nhánh nhiệm vụ: week3/nhut-database

## 1. Mục tiêu

Đối chiếu mã nguồn đã commit giữa san-nha-team và
sports-court-booking; ghi nhận file còn thiếu và file khác nội dung
để nhóm tiếp tục hoàn thiện, chuẩn bị giai đoạn kiểm thử.

## 2. Phiên bản đối chiếu

- Commit nguồn san-nha-team: a6a81c97dd5449e5b845c6453b1d79c5147297a4
- Commit đích sports-court-booking: cfeacb7708bea186d80825241f5746a005b5799c
- Phạm vi: src, scripts, Maven Wrapper, pom.xml và cấu hình triển khai.
- Chỉ kiểm kê file đã commit; không bao gồm thay đổi chưa commit.
- Đối chiếu nội dung bằng Git blob hash; chưa phân tích ngữ nghĩa.

## 3. Kết quả kiểm kê

- Tổng số file nguồn trong phạm vi: 185
- Số file cùng đường dẫn và cùng nội dung: 105
- Số file chưa có ở repo tổng: 64
- Số file cùng đường dẫn nhưng khác nội dung: 16

Danh sách chi tiết:
- T6-file-con-thieu.txt
- T6-file-khac-noi-dung.txt

File khác nội dung chưa đồng nghĩa có lỗi hoặc cần ghi đè.
Cần đối chiếu thay đổi của các thành viên trước khi xử lý.

## 4. Kết quả tích hợp đã có minh chứng

- T2: 4b221664ccdea977353c533b2ae5a31fd43d50c6
  Bổ sung Maven Wrapper và cấu hình nền.
- T3: e0a3bf1ae73c8bfa72f8fc51bd7823fe894f3834
  Bổ sung 4 lớp quản trị tài khoản, 146 dòng.
- T4: 13917e8f9f0a31c563545e2ac582c5645238abd9
  Bổ sung 3 lớp khởi tạo và kiểm soát database, 162 dòng.
- T5: c5645ff75780f56a4983a02d779d9afc312bfc9f
  Bổ sung 15 file triển khai và script, 1.063 dòng.
- PR số 26 đã merge T4 và T5 vào main:
  https://github.com/nhut5768/sports-court-booking/pull/26

## 5. Bàn giao và công việc tiếp theo

- Nhóm kiểm tra danh sách file thiếu theo phần việc được phân công.
- Đối chiếu file khác nội dung trước khi quyết định tích hợp.
- Không ghi đè thay đổi của thành viên chỉ để giống nguồn.
- Không sửa migration đã chạy.
- Không đưa mật khẩu, API key, backup và dữ liệu riêng tư lên Git.
- Chuẩn bị môi trường và kịch bản kiểm thử cho giai đoạn tiếp theo.

## 6. Phạm vi xác nhận

Nhiệm vụ T6 này kiểm kê mã nguồn và bổ sung tài liệu bàn giao.
Không thực hiện build, kiểm thử chức năng, deploy hoặc chuyển dữ liệu.
Chưa kết luận toàn bộ dự án hoàn chỉnh chỉ từ kết quả kiểm kê file.