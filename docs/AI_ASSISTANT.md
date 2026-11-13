# Trợ lý Gemini riêng

Hỏi AI là mục riêng với tin nhắn khách–chủ sân. Không tự trả lời trong hội thoại chủ sân.
Xem [RUN_LOCAL.md](RUN_LOCAL.md) để chọn model thực tế bằng key và chạy localhost.

## Cấu hình và chạy
Backend chỉ gọi Google Gemini generateContent:
https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent
GEMINI_MODEL bắt buộc, không có mặc định; chọn từ models.list bằng script local. Không cần OPENAI_API_KEY; mã chạy không đọc key OpenAI,
không có fallback hoặc retry sang model/nhà cung cấp khác, không tự bật billing.

1. Tạo Gemini API key tại https://aistudio.google.com/apikey bằng project đủ điều kiện Free Tier.
2. Kiểm tra tier và hạn mức của project trong Google AI Studio. Giữ project ở Free Tier để tránh phí.
   Chọn model có Free Tier theo https://ai.google.dev/gemini-api/docs/pricing.
   API không có cờ ép miễn phí; cùng endpoint có thể tính phí nếu bạn chủ động dùng project Paid Tier.
   Hết quota trả lỗi, ứng dụng không tự nâng cấp, đổi model hoặc gọi dịch vụ khác.
3. Trong PowerShell chạy ứng dụng, nhập key an toàn (không ghi vào file):

    $geminiSecret = Read-Host "Gemini API key" -AsSecureString
    $env:GEMINI_API_KEY = [System.Net.NetworkCredential]::new("", $geminiSecret).Password
    $env:GEMINI_MODEL = "<model trả về từ models.list>"
    # Cấu hình DB_USERNAME, DB_PASSWORD và Google OAuth như hướng dẫn hiện có
    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql" "-Dspring-boot.run.arguments=--server.port=8082"

Dừng bản cũ trên cổng 8082 trước khi chạy. Cấu hình application.properties dùng chung mysql/demo/prod.
Cần khởi động lại Java sau khi đặt biến môi trường. Không dùng Google OAuth client secret làm Gemini key.
Không có GEMINI_API_KEY: website vẫn chạy, chat báo chưa khả dụng, POST trả 503, kể cả có key OpenAI cũ.
Key chỉ nằm phía server, gửi trong header x-goog-api-key; không đưa vào URL, HTML, JS hoặc log.

## Chạy thử
1. Mở http://localhost:8082, bấm Hỏi Sân Nhà ở góc dưới bên phải.
2. Gửi “Tìm sân cầu lông”: trợ lý hỏi thêm khu vực, ngày và giờ.
3. Gửi “Thủ Đức ngày mai 19h, tối đa 200 nghìn mỗi giờ”: backend lấy tối đa 60 sân đang hoạt động,
   dùng BookingService.slots() kiểm tra giờ FREE và giá của chính khung giờ, hiển thị tối đa 5 kết quả.
4. Bấm liên kết sân để chọn giờ và đặt trên website; trợ lý không đặt thay bạn.
5. Thử “Sân nào còn trống tối nay?”: ngày lấy theo Asia/Ho_Chi_Minh, hỏi lại giờ cụ thể/môn/khu vực.
6. Thử “Hướng dẫn hủy đặt sân”: hướng dẫn theo chính sách code (khách hủy trước trên 2 giờ;
   khách hủy đơn đã cọc thì bị giữ cọc), liên kết /bookings. Không tự hủy hoặc hoàn tiền.
7. Thử thiếu key, key sai, ngắt mạng, gửi 9 tin trong một phút, tin dài hơn 1.000 ký tự.

## Luồng và phạm vi dữ liệu
Trình duyệt → Spring Security + CSRF → controller giới hạn yêu cầu → Gemini trích xuất JSON theo schema
→ backend kiểm tra bộ lọc → CourtRepository.search() + BookingService.slots() → câu trả lời tiếng Việt
và liên kết do server tạo. AI không tự viết tên sân, giá, trạng thái trống hoặc URL.

Chỉ hỗ trợ môn/khu vực và các khung tròn giờ trong dữ liệu hiện có, ngày từ hôm nay đến 30 ngày tới.
Bộ lọc giá áp dụng giá khung giờ, không lấy giá tối thiểu quảng cáo làm giá thực tế.
Kết quả là ảnh chụp tại thời điểm hỏi, trang đặt sân kiểm tra lại. Nếu nhiều hơn 60 sân phù hợp bộ lọc,
trợ lý không bảo đảm đã xét toàn bộ. Không gửi entity, SQL, thông tin chủ sân/khách hoặc đơn cá nhân
cho mô hình. Không có công cụ thay đổi dữ liệu, đặt/hủy sân/thanh toán hoặc chạy SQL tùy ý.
Đầu vào người dùng và tối đa 8 tin lịch sử gần nhất (4 lượt) được gửi tới Gemini.
Không gửi mật khẩu, dữ liệu cá nhân nhạy cảm vào chat. Free Tier có thể dùng nội dung để cải thiện
sản phẩm theo điều khoản Google: https://ai.google.dev/gemini-api/terms. Không dùng cờ store của OpenAI.

## Giới hạn và vận hành
- 1.000 ký tự/tin; body tối đa 8 KiB trước JSON parsing.
- Tối đa 8 yêu cầu/phút/session, 40/phút toàn tiến trình và 4 yêu cầu AI đồng thời.
- Mỗi session chỉ xử lý một tin tại một thời điểm; xóa hội thoại không đặt lại bộ đếm rate limit.
- Server giữ tối đa 8 tin, xóa ngữ cảnh sau 20 phút không tương tác; không lưu hội thoại vào database.
- Trình duyệt giữ tối đa 18 bong bóng trong bộ nhớ trang, không localStorage.
- Timeout kết nối 4 giây, toàn bộ gọi AI 15 giây, trình duyệt 18 giây; tối đa 400 output tokens, không tự retry.
- Lỗi/quota/refusal/response không hợp lệ trả 503 chung, không lộ nội dung lỗi upstream hoặc key.
- CSRF bật cho POST/DELETE, không CORS mở; DOM dùng textContent, chỉ render URL nội bộ theo allowlist.
- Widget hỗ trợ Escape, nhãn truy cập, trạng thái đang trả lời, xóa hội thoại và màn hình nhỏ.
- Giới hạn hiện tại theo bộ nhớ từng instance. Khi triển khai nhiều instance/public quy mô lớn,
  dùng shared rate limiter (Redis/gateway), thêm chống bot/IP quota ở proxy tin cậy và ngân sách nhà cung cấp.
  Session cookie mới không vượt được trần 40 request/phút/instance nhưng có thể gây nghẽn cho người khác.
- Cấu hình log không ghi HTTP Authorization, body chat hoặc nội dung lỗi provider.

## Kiểm thử
    .\mvnw.cmd test
    .\mvnw.cmd test "-DsanNha.uiSnapshots=true"
    node scripts/ui-smoke.mjs

AssistantIntegrationTest kiểm tra dữ liệu thật trong H2, sân khóa/ngừng hoạt động, lọc giá,
thiếu ngày giờ, hướng dẫn hủy, CSRF, độ dài, thiếu key/lỗi upstream, lịch sử và rate limit.
AiIntentClientTest dùng HTTP server local để kiểm tra đúng path Gemini, header x-goog-api-key,
contents/parts, role model, JSON schema, blocked output, lỗi quota và không lộ key.
Kiểm thử từ POST /api/assistant/messages đi qua client HTTP Gemini rồi về service dữ liệu.
Endpoint production cố định Google, tên model được kiểm tra để không chèn host/path khác.
Test tự động không gọi dịch vụ ngoài hoặc phát sinh phí.

### Kiểm chứng Gemini thật
Khi đã đặt key và khởi động lại, mở widget gửi hai tin ở phần Chạy thử.
GET /api/assistant/status chỉ xác nhận có key, không chứng minh key/quota hợp lệ.
Mã HTTP 200 cùng câu hỏi bổ sung/kết quả sân từ POST /api/assistant/messages cho biết luồng đã hoàn tất.
Kiểm tra usage của project/model trong Google AI Studio để xác nhận request đến Gemini.
Không bật log Authorization/x-goog-api-key hoặc sao chép key vào báo cáo.
Ghi chép kiểm chứng từ phiên cũ (không được tái xác minh trong phiên sửa hiện tại vì thiếu key):
POST chat trả 200 cho câu hỏi thiếu thông tin và câu hỏi tìm sân theo môn/khu vực/ngày/giờ/giá.
Google trả 404 với model 2.5 Flash-Lite do không còn mở cho người dùng mới; cấu hình đã đổi rõ ràng
sang 3.5 Flash-Lite, không phải fallback tự động. Key chỉ nằm trong môi trường tiến trình server.

Tài liệu Google chính thức:
- https://ai.google.dev/gemini-api/docs/generate-content/structured-output
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/rate-limits
