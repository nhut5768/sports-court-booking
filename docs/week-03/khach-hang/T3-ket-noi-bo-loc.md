# KAN-25 — T3: Kết nối bộ lọc tìm sân

Ngày thực hiện: 10/11/2026. Nhánh: `week3/nhu-luong-khach-hang`.

## Nội dung cập nhật

- Tái sử dụng `HomeController`, `CourtRepository` và `home.html`; không tạo controller, repository hoặc template trùng với tuần 2.
- Chuẩn hóa từ khóa, môn thể thao và khu vực: bỏ khoảng trắng đầu/cuối; tên môn/khu vực không phân biệt chữ hoa/thường khi khớp lựa chọn có sẵn. Giá trị không có trong danh sách vẫn được tìm và có thể trả kết quả rỗng.
- Cấp danh sách khu vực gồm danh mục chung và các khu vực thực tế của sân đang hoạt động; khu vực ngoài danh mục vẫn chọn được.
- Cấp ngày hôm nay cho giới hạn dưới của ô ngày ngay trong controller trang chủ.
- Giữ giá đã chuẩn hóa trong hai form và liên kết phân trang; form đổi bộ lọc không gửi trang cũ, nên quay về trang đầu.
- Giữ liên kết chi tiết theo ngày đã chọn. Chưa bổ sung luồng quay lại giữ toàn bộ bộ lọc; phần này nằm ngoài thay đổi T3 hiện tại.
- Cập nhật cuối tuần: luồng quay lại giữ bộ lọc được hoàn thiện ở [T5](T5-ket-qua-gia-phan-trang.md); ghi nhận trên mô tả phạm vi commit T3.

## Quy tắc tìm kiếm

| Điều kiện | Hành vi |
| --- | --- |
| Môn và khu vực | Kết hợp đồng thời với từ khóa và khoảng giá; chỉ tìm sân đang hoạt động |
| Ngày | Từ hôm nay đến 30 ngày tới; dùng để hiển thị số khung còn trống và mở lịch chi tiết đúng ngày |
| Sân hết giờ trống | Vẫn hiển thị với trạng thái hết giờ; khách có thể đổi ngày |
| Giá từ/đến | So với `minPrice` của sân, bao gồm hai biên; không phải bộ lọc giá của từng khung giờ |
| Giá trống | Không giới hạn phía tương ứng |
| Giá âm, không phải số hoặc giá từ lớn hơn giá đến | Từ chối yêu cầu |
| Ngày sai định dạng hoặc ngoài phạm vi | Từ chối yêu cầu |
| Không có kết quả | Hiển thị trạng thái rỗng và liên kết xóa bộ lọc |

## Kiểm thử bổ sung

Trong `CourtIntegrationTest`:

1. `combinedSearchNormalizesFiltersAndReturnsOnlyMatchingActiveCourts`: kết hợp từ khóa, môn, khu vực, ngày và hai biên giá; loại sân khác môn/khu vực, giá cao và sân ngừng hoạt động.
2. `searchAreasIncludeActualCourtAreasAndFiltersSurviveBothFormsAndPaging`: khu vực thực tế ngoài danh mục; giữ điều kiện giữa hai form và sang trang 2; form không giữ chỉ số trang cũ.
3. `searchHandlesBlankFiltersNoMatchesAndInvalidRanges`: bộ lọc rỗng, không có kết quả, giá sai, ngày sai và biên ngày tối đa.

## Xác minh

Lệnh kiểm thử trên checkout GitHub:

```powershell
mvn -o -Dtest=CourtIntegrationTest test
```

Sau khi cập nhật main đã tích hợp công việc nhóm, build vẫn dừng ở biên dịch do thiếu `ReviewService`, `NotFoundException`, `GoogleOidcUserService`. Chưa thể xác nhận toàn bộ checkout GitHub build thành công.

Để kiểm tra riêng thay đổi T3, tạo bản sao tạm của mã local đầy đủ trong `target/t3-validation` rồi chép đè đúng bốn tệp T3 và chạy:

```powershell
mvn -o -f target/t3-validation/pom.xml -Dtest=CourtIntegrationTest test
```

Bản kiểm chứng dùng H2/MockMvc, không gọi database thật. Các tệp nền từ bản local chỉ dùng trong thư mục tạm, không nằm trong commit.

Kết quả: **BUILD SUCCESS — 47 tests, 0 failures, 0 errors, 0 skipped**, gồm 44 ca sẵn có và 3 ca T3 mới. Thời gian kiểm thử: 49,94 giây. Kết quả này xác minh thay đổi T3 với mã nền local đầy đủ; không thay thế kết quả build độc lập của checkout GitHub. Chưa kiểm tra thao tác trên trình duyệt hoặc MySQL thật.

Kiểm tra `git diff --check`: đạt. Commit chỉ gồm bốn tệp mã/kiểm thử T3 và tài liệu này, không gồm `target`, log hoặc các lớp nền dùng để kiểm chứng.
