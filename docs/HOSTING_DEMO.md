# Render Free + MySQL bền vững — Sân Nhà

Đây là phương án triển khai chính thay cho Oracle, đối chiếu 02/11/2026.
Dockerfile và render.yaml nằm ngay gốc repo, nhánh main. Không cần tunnel hoặc máy cá nhân chạy thường trực.
Chưa tạo service hay kích hoạt thanh toán trong quá trình chuẩn bị code.

## Kiến trúc và giới hạn

Render Free chạy Docker Java 21, HTTPS tại https://<ten-thuc-te>.onrender.com, một instance.
Aiven Free MySQL giữ tài khoản, sân, đơn, chat và **cả ảnh gốc** trong bảng stored_uploads (migration V14 mới).
Render chỉ có cache ảnh tạm trong /tmp; restart/ngủ/redeploy sẽ tạo lại cache từ MySQL.
Không gắn persistent disk trả phí, không upload ảnh vào Git. Không tự gọi OpenAI hoặc AI trả phí.

- Render ngủ sau 15 phút không có inbound traffic, cold start khoảng một phút; 750 giờ/workspace/tháng.
  Không có thời hạn 30 ngày cho free web service được nêu trong tài liệu; Free PostgreSQL mới có hạn 30 ngày, không dùng ở đây.
  Khi hết quota, tài khoản không payment method có thể bị suspend/build bị dừng; không thêm billing để tự mua thêm.
  Chính sách xác minh tài khoản có thể yêu cầu thêm thông tin; nếu yêu cầu trả tiền thì dừng.
- Aiven Free MySQL không cần thẻ, không thời hạn cố định: 1 CPU, 1 GB RAM, **1 GB tổng disk**,
  backups, một free MySQL/org. Có thể tắt service không hoạt động và thông báo trước.
- Ảnh chiếm dung lượng cùng DB, không phù hợp gallery lớn: profile Render giới hạn 4 MB/ảnh, tối đa 34 MB/request.
  Nén ảnh trước upload, theo dõi disk Aiven và lưu backup riêng. Không tự nâng gói khi đầy.
- JVM heap 256 MB để chừa bộ nhớ native trong free instance. Demo nhiều người cùng lúc có thể chậm hoặc hết RAM.
- Một app instance; session/rate limiter/SSE trong RAM. Restart yêu cầu đăng nhập lại, chat/đơn đã commit còn trong DB.
- Google, Gemini và MySQL cần outbound Internet; Render có thể suspend service khi outbound bất thường.
- Gemini phải là project Free Tier; server không xác định billing tier từ key. Hết quota báo lỗi, không fallback.
- Không có SLA 24/7 cho dịch vụ miễn phí.

Nguồn chính thức:
https://render.com/docs/free
https://render.com/docs/blueprint-spec
https://aiven.io/docs/products/mysql/concepts/mysql-free-tier
https://ai.google.dev/gemini-api/docs/billing

## 1. MySQL miễn phí và TLS

1. Tạo **Aiven for MySQL Free**, kiểm tra plan Free và giá $0, không chọn trial trả phí hay nâng cấp.
2. Lấy hostname, port, database (thường defaultdb), user, password và tải CA certificate.
3. Trong Render service → Environment → Secret Files, thêm tên **aiven-ca.pem**,
   dán toàn bộ nội dung CA PEM đã tải (BEGIN CERTIFICATE đến END CERTIFICATE).
   Render Secret Files là văn bản, không upload file PKCS12 nhị phân.
4. Startup Docker tự tạo /tmp/aiven-truststore.p12 bằng keytool từ CA công khai.
   Mật khẩu truststore changeit chỉ bảo vệ CA công khai, không phải password MySQL.
   Không đưa CA/truststore hay password DB vào Git.
5. Điền DB_URL trong Render, thay HOST/PORT/DB thật:

       jdbc:mysql://HOST:PORT/DB?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:/tmp/aiven-truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8

   DB_USERNAME và DB_PASSWORD là biến riêng, không nhét password DB vào URL.
   Không tắt TLS/verify để chữa lỗi kết nối.
   Hướng dẫn Java/TLS: https://aiven.io/docs/products/mysql/howto/connect-with-java

## 2. Tạo Render service

Dùng **New → Web Service** để có thể thêm Secret File aiven-ca.pem trước lần deploy thành công.
- Repository: nhut5768/san-nha-team
- Branch: main
- Language/runtime: Docker
- Root Directory: để trống (gốc repo)
- Dockerfile Path: ./Dockerfile
- Docker Build Context: .
- Instance Type: **Free**
- Health Check Path: /health
- Không điền Build Command/Start Command của Java; Dockerfile đã có ENTRYPOINT.
- Auto deploy: tắt khi thử cấu hình, chỉ Manual Deploy sau khi đầy đủ env và Secret File.

Mẫu cấu hình tương đương là render.yaml, chỉ có một web service plan free.
Nếu dùng New → Blueprint, chọn main và file render.yaml; nhập biến sync:false khi được hỏi.
Lần khởi động đầu có thể fail do chưa thêm Secret File; thêm file rồi Manual Deploy lại, không đổi sang paid.
Với service đã tồn tại, các env sync:false mới phải thêm thủ công trong Dashboard.

## 3. Biến môi trường server

Điền đúng các biến trong render.yaml vào Render Environment:

| Biến | Giá trị |
| --- | --- |
| SPRING_PROFILES_ACTIVE | prod,mysql,hosting,render |
| JAVA_TOOL_OPTIONS | -Xms64m -Xmx256m -Xss512k -XX:MaxDirectMemorySize=32m -XX:+ExitOnOutOfMemoryError |
| DEBUG / TRACE | false |
| DB_URL / DB_USERNAME / DB_PASSWORD | MySQL Aiven và TLS như trên |
| GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET | OAuth web client của bạn |
| GEMINI_API_KEY | Key project Free Tier trong AI Studio |
| GEMINI_MODEL | Model project được phép dùng; mặc định dự án gemini-3.5-flash-lite |

Không cần OPENAI_API_KEY. Render cấp PORT, profile render tự đọc; không hardcode 8082.
Không đặt API key vào Docker build arg, render.yaml, source, JS hay .env được commit.
Để trống Google key thì không hiện nút Google; thiếu Gemini key thì chat AI báo chưa khả dụng, chat người vẫn chạy.
Không chia sẻ docker inspect, env dump hay log debug chứa thông tin nhạy cảm.

## 4. Google và chat theo hostname mới

Sau khi Render cấp URL thật, Google Cloud → Google Auth Platform → Clients → Web client:
thêm callback chính xác:

    https://TEN-THUC-TE.onrender.com/login/oauth2/code/google

Nếu cần JavaScript origin: https://TEN-THUC-TE.onrender.com, không path.
Thêm email thành viên vào Test users theo cấu hình audience/testing.
Prod dùng secure session cookie và forwarded headers; Render kết thúc HTTPS và chuyển request tới container.
Tất cả URL chat/AI là cùng origin. SSE giữ quyền theo phiên, không cần thêm CORS hay đổi senderId.
Khách Google mới chỉ có CUSTOMER; email xung đột vẫn không được tự liên kết.
Không cần Caddy/DuckDNS/Oracle cho phương án này.

## 5. Dữ liệu và owner cho bản demo

Bản code local mới dùng schema V15, chưa deploy lên Render theo yêu cầu hiện tại.
Chủ sân tự đăng ký tại /register/owner, tự đặt mật khẩu rồi đăng nhập /owner để thêm sân/khung giờ/ảnh/QR.
Khách hàng đăng ký tại /register/customer hoặc /register; Google lần đầu chỉ tạo CUSTOMER.
Không còn lệnh APP_OWNER_CREATE hoặc API admin tạo owner/đổi role. Không đổi role khi email đã tồn tại.
Khi được phép triển khai bản này sau, cần chạy migration bằng tài khoản deployment riêng theo SHARED_DATABASE.md.
Từ profile render, ảnh vẫn lưu MySQL tự động.

Nếu chuyển DB cũ: backup/import qua MySQL client trên máy quản trị, gồm flyway_schema_history, không commit dump.
Ảnh cũ nằm trong uploads của máy bạn **không tự xuất hiện** trong DB cloud.
Không import metadata ảnh cũ rồi giả định Render có file: cần re-upload qua giao diện hoặc chuyển từng ảnh vào
stored_uploads với đúng object_key courts/<courtId>/<storedName> hoặc payment-qr/<ownerId>/<storedName>
bằng công cụ quản trị có kiểm soát. Khuyến nghị demo mới: tạo dữ liệu và upload lại trên cloud.
Dữ liệu lịch sử từng có trong Git vẫn còn ở commit cũ; lần này chỉ bỏ theo dõi ở tip, không rewrite lịch sử của nhóm.

## 6. Kiểm tra sau deploy

1. Mở URL HTTPS từ điện thoại 4G hoặc máy thành viên; /health phải 200 UP.
2. Login email/password và Google; callback không còn localhost.
3. Hai browser đăng nhập khách/chủ: gửi tin hai chiều không reload; AI dừng sau owner trả lời,
   gợi ý hỏi AI vẫn dùng được; SSE reconnect tải lại lịch sử.
4. Thử Gemini thật với ngày/giờ/khu vực; thiếu quota báo lỗi, không tạo câu trả lời giả.
5. Upload ảnh và QR, đặt sân, gửi chat; Manual Deploy lại hoặc chờ app ngủ,
   đăng nhập lại và xác nhận ảnh/đơn/chat còn nguyên.
6. Tắt máy cá nhân, truy cập bằng thiết bị khác.

Nếu health503: kiểm tra DB_URL, truststore, credentials, trạng thái Aiven, Flyway; không dùng ddl-auto=create.
Nếu app không boot vì RAM: giảm kích thước ảnh/tải demo, không tự nâng gói có phí.
Nếu Google mismatch: sửa đúng hostname callback trong Google Console.
Render Free không có shell/one-off job; dùng Environment cho bootstrap, client ngoài cho backup DB.

## 7. Kiểm tra trước Git

- Dockerfile và render.yaml phải được git ls-files liệt kê trên main.
- .env, /uploads, dump database/sports_court_booking.sql, backup và truststore bị bỏ qua.
- Migration src/main/resources/db/migration là schema code có chủ ý, không phải database dump.
- Kiểm tra secret trên toàn bộ file sắp commit, không chỉ Dockerfile.
- Chỉ push fast-forward lên main; không force push hoặc rewrite lịch sử.

## Kết quả xác minh trước commit

- Maven verify: 86 kiểm thử đạt, build JAR thành công.
- DatabaseImageStorageTest: ảnh đọc lại sau mất cache, tách QR/ảnh sân, delete, transaction rollback và chặn đường dẫn/nội dung giả.
- HostingProxyIntegrationTest: callback HTTPS, cookie an toàn, CSRF và API yêu cầu login.
- Render YAML đã parse, plan free, một service, không disk/database trả phí; shell startup qua bash -n.
- Quét nội dung Git index không phát hiện các mẫu key đã kiểm tra; không có .env, uploads, dump, truststore trong cây file main mới.
- Chưa cài Docker ở máy này, chưa chạy image hoặc gọi Render/Aiven/Google/Gemini thật theo hostname cloud.

## AI không được chặn website khởi động

GEMINI_MODEL chỉ nhập tên model, ví dụ gemini-3.5-flash-lite; không thêm models/, dấu ngoặc kép, URL hay GEMINI_MODEL=.
Key rỗng hoặc model sai định dạng làm /api/assistant/status trả available:false và gửi chat AI trả 503 dễ hiểu.
Website, đăng nhập và nhắn tin chủ sân vẫn hoạt động. HTTP client được tạo khi có yêu cầu AI hợp lệ, không trong constructor Spring.
Model đúng định dạng nhưng không có quyền/quota hoặc key bị provider từ chối được xử lý 503 khi gọi; không fallback trả phí.
Sau sửa env trên Render cần Save and deploy; xem /health và /api/assistant/status để kiểm tra, không in env/key vào log.

Chuyển dữ liệu local hiện có, không seed/ghi đè: xem [LOCAL_TO_AIVEN.md](LOCAL_TO_AIVEN.md) và scripts/aiven-data.ps1.

Chế độ làm việc chung cho nhóm, user MySQL riêng và migration riêng: [SHARED_DATABASE.md](SHARED_DATABASE.md).
