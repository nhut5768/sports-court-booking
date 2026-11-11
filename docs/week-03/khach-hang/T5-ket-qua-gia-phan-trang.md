# KAN-25 — T5: Kết quả tìm kiếm, giá, phân trang và trạng thái

Lịch công việc: thứ Năm, 12/11/2026. Thực hiện sớm: 11/11/2026.
Nhánh: `week3/nhu-luong-khach-hang`.

## Thay đổi

- Chặn tình trạng trang kết quả rỗng do số trang vượt phạm vi: nếu còn kết quả, tải trang cuối hợp lệ. Số trang âm được đưa về trang đầu như trước.
- Hiển thị phạm vi kết quả thực tế, ví dụ “Hiển thị 9–9 trong 9 sân phù hợp”; không hiển thị phạm vi sai khi không có kết quả.
- Khi chưa chọn ngày, thẻ sân hướng dẫn “Chọn ngày để xem lịch trống”. Khi chọn ngày, giữ trạng thái có hoặc hết khung trống đã tích hợp T3.
- Giữ nguyên nghĩa của giá “Từ”: giá thấp nhất của sân; giá khung giờ và cọc được tính ở bước xác nhận. Không đổi quy tắc giá/cọc T4.
- Thêm `CustomerNavigation` làm thành phần hỗ trợ dùng chung để mang từ khóa, môn, khu vực, ngày, khoảng giá và trang kết quả qua các trang khách hàng.
- Giữ ngữ cảnh qua liên kết chi tiết, biểu mẫu đổi ngày, phân trang đánh giá, trang xác nhận và POST bị từ chối. Quay lại danh sách phục hồi điều kiện tìm kiếm ban đầu, kể cả khi đã chọn ngày khác ở chi tiết.
- Chỉ cho phép liên kết quay về `/` hoặc chuỗi truy vấn của trang chủ; bỏ đích ngoài website, đường dẫn không liên quan và ký tự điều khiển.

Không tạo lại controller, repository hoặc template tuần 2. Sửa trực tiếp các điểm nối trên thành phần hiện có.

## Kiểm thử mới

1. `searchClampsPageAndReportsActualResultRangeAndEmptyState`: số trang vượt giới hạn/âm, phạm vi kết quả và trạng thái rỗng/chưa chọn ngày.
2. `originalSearchSurvivesDetailDateChangeConfirmationAndReturn`: đi theo liên kết đã render, đổi ngày, mở xác nhận và quay về đúng bộ lọc/trang ban đầu, gồm ký tự tiếng Việt.
3. `returnLinksRejectExternalAndUnrelatedDestinations`: từ chối đích quay lại ngoài trang chủ.
4. `stalePriceReturnsToDetailWithOriginalSearchContext`: giá không còn khớp thì không tạo đơn, giữ đúng sân/ngày và ngữ cảnh tìm kiếm khi quay về.

Kết quả kiểm chứng cuối tuần và giới hạn môi trường được tổng hợp trong [tài liệu T6](T6-ban-giao-luong-khach-hang.md).
