# KAN-18: Kết quả kiểm tra luồng đặt sân

- Ngày thực hiện: 06/11/2026.
- Nhánh: `week2/nhu-dat-san`.
- Phạm vi: tìm sân, lịch trống, giá và xác nhận đặt.
- Kiểm thử: `src/test/java/vn/uth/court/CourtIntegrationTest.java`.
- Môi trường: Java 21, Spring Boot, H2 và MockMvc.

## Lệnh và kết quả

```powershell
.\mvnw.cmd -Dtest=CourtIntegrationTest test
```

BUILD SUCCESS. Toàn bộ lớp kiểm thử có 44 ca đạt, 0 thất bại, 0 lỗi, 0 bỏ qua; thời gian kiểm thử 25,24 giây. Số 44 bao gồm cả các ca ngoài phạm vi nhiệm vụ này.

## Nội dung đã kiểm tra

| Hạng mục | Bằng chứng kiểm thử | Kết quả |
| --- | --- | --- |
| Tìm sân | `publicPagesRender`, `professionalUiContractsAndPreview`: danh sách sân render; tham số môn và giá được giữ trong giao diện bộ lọc | Đạt trong phạm vi render; chưa kiểm chứng đầy đủ mọi tổ hợp lọc |
| Lịch trống | `discoveryDateUsesRealAvailabilityAndKeepsSelectedDate`: sân mở 18–20 giờ, đã đặt lúc 19 giờ thì còn 1 khung; giữ ngày đã chọn; từ chối ngày quá khứ | Đạt |
| Khóa và trả giờ | `lockAndUnlockSlot`, `cancellationReleasesSlot`: giờ khóa không đặt được; mở khóa hoặc hủy đơn thì đặt lại được | Đạt |
| Giá | `priceSnapshotAndDuplicate`, `peakPriceIsSnapshotted`: giá thường 120.000 đồng, cao điểm 180.000 đồng; đổi bảng giá không làm đổi giá đơn đã tạo | Đạt |
| Tiền cọc | `depositIsHalfRoundedUpToThousand`: cọc 50%, làm tròn lên nghìn đồng; 120.000 đồng cọc 60.000 đồng, 185.000 đồng cọc 93.000 đồng | Đạt |
| Xác nhận đặt | `authenticatedPagesRender`, `securityAndCsrfAreEnforced`: trang xác nhận giờ trống trả 200; giờ đã đặt trả 400; POST hợp lệ tạo đơn và chuyển đến chi tiết | Đạt |
| Chống đặt trùng | `concurrentBookingsOnlyOneWins`: hai yêu cầu cùng sân/ngày/giờ chỉ một thành công, database có một đơn | Đạt |
| Điều kiện đặt | `dateAndHoursAreValidated`, `inactiveCourtCannotBeBooked`, `ownerAccountCannotBook`: chặn ngày ngoài phạm vi, giờ không hợp lệ, sân ngừng hoạt động và tài khoản chủ sân đặt sân | Đạt |

## Trạng thái đơn và giới hạn

Tạo đơn thành công giữ khung giờ ở trạng thái `PENDING_DEPOSIT`; chưa đồng nghĩa chủ sân đã xác nhận nhận cọc. Luồng cam kết, báo chuyển và xác nhận cọc có các kiểm thử riêng trong cùng lớp.

Kết quả dựa trên kiểm thử tự động H2/MockMvc, chưa xác minh bằng trình duyệt, MySQL thật hoặc chuyển khoản thực tế. Không thay đổi mã nguồn chức năng trong nhiệm vụ này; bàn giao báo cáo kết quả kiểm thử hiện có.
