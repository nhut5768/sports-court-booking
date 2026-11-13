# Một MySQL Aiven cho Render và toàn nhóm

## Mô hình và trạng thái

- Database chung: mysql-3bbe504b-ut-59dc.l.aivencloud.com:23004/defaultdb.
- Máy thành viên chỉ bật profile shared. Không cần MySQL localhost; không H2, không fallback khi mất mạng.
- Render giữ prod,mysql,hosting,render, HTTPS proxy và cookie Secure. Shared dùng HTTP 127.0.0.1:8082,
  cookie HttpOnly/SameSite=Lax không Secure, không tin forwarded headers.
- Cả hai dùng stored_uploads trong MySQL, /tmp chỉ là cache có thể xóa/restart.
- Shared tắt Flyway, schema validate-only, seed và app.owner.create đều false. Guard kiểm tra trước khi mở datasource.
- Pool: 2 connection/máy, Render 4; theo dõi tổng so với giới hạn Aiven 76, kể cả job/phiên quản trị.
- Không có thay đổi schema/migration cho cấu hình này. Schema V14 phải có sẵn.
- Chưa tạo user Aiven hoặc chuyển dữ liệu thực tế vì credentials quản trị chưa có tại máy.
  Không lấy password từ Render hoặc yêu cầu gửi vào chat.

## 1. Quản trị viên tạo user riêng — không dùng chung avnadmin

avnadmin chỉ do quản trị viên giữ. Dùng client quản trị TLS VERIFY_IDENTITY và CA tải từ chính service.
Mỗi thành viên chọn một tên riêng, ví dụ san_nha_thanh, san_nha_nhut. Đây là user MySQL, khác tài khoản đăng nhập website.

Sinh SQL không chứa mật khẩu vào thư mục private bị Git bỏ qua:

    New-Item -ItemType Directory -Force backups | Out-Null
    .\scripts\shared-grants.ps1 -Members san_nha_thanh,san_nha_nhut | Set-Content -Encoding utf8 backups/shared-grants.sql

Mở và kiểm tra tên trước khi chạy bằng avnadmin trong client quản trị an toàn.
Không chạy lại nếu user đã tồn tại; CREATE USER có chủ ý không IF NOT EXISTS để tránh tự đổi quyền account cũ.
Nếu CREATE báo lỗi thì dừng, kiểm tra SHOW GRANTS trước khi tiếp tục.

- san_nha_render và từng thành viên: SELECT/INSERT/UPDATE/DELETE trên 11 bảng nghiệp vụ,
  gồm stored_uploads; không được CREATE/ALTER/DROP/INDEX/GRANT và không có DML trên flyway_schema_history.
- san_nha_migrator: DML + CREATE/ALTER/DROP/INDEX/REFERENCES trong defaultdb, chỉ phục vụ deployment/Flyway.
- Không global grants, không GRANT OPTION, không CREATE USER cho member/migrator.
- Tất cả user REQUIRE SSL. Client còn bắt buộc xác minh CA và hostname bằng VERIFY_IDENTITY.
- SQL CREATE USER dùng RANDOM PASSWORD: kết quả do MySQL sinh chỉ lưu vào password manager và gửi riêng đúng người.
  Không chụp/gửi output chứa password vào chat, không lưu trong SQL, Git hoặc log dùng chung.
- Kiểm tra SHOW GRANTS từng user: chỉ các quyền trên. Nếu Aiven Console tạo user với quyền mặc định admin,
  không phát user đó cho thành viên; dùng account mới/grants tối thiểu có kiểm soát.
- Tạo account DB không tạo account OWNER trên website. Quyền web vẫn do dữ liệu accounts hiện có quyết định.
  Người có DB DML được tin cậy với dữ liệu nhóm; đây không phải cơ chế phân quyền khách của website.

Tham khảo Aiven: https://aiven.io/docs/products/mysql/howto/manage-service-users

## 2. Thành viên chạy shared

Cài JDK 21, clone phiên bản có profile shared; chỉ cần Internet và CA PEM, không cần MySQL server.
Tại thư mục dự án:

    .\scripts\run-shared.ps1 -CaPath "$env:USERPROFILE\Downloads\ca.pem" -Username san_nha_thanh

Thay tên user và đường dẫn CA thực tế. Script hỏi password ẩn, tạo truststore PKCS12 trong temp,
đặt biến môi trường cho tiến trình Java rồi chạy Maven với đúng profile shared. Khi thoát, phục hồi/xóa biến password.
Không đưa key/CA/truststore vào Git. Nếu execution policy chặn, dùng một tiến trình riêng:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-shared.ps1 -CaPath "C:\path\ca.pem" -Username san_nha_thanh

Dừng tiến trình Spring Boot local cũ trước để tránh trùng cổng; không cần xóa hoặc dừng MySQL nguồn.
Vào http://localhost:8082. Mỗi máy chạy riêng phiên đăng nhập, nhưng cùng dữ liệu MySQL.
Nếu đổi cổng: thêm -Port 8083, và thêm callback tương ứng ở Google Cloud nếu dùng Google.

Biến cấu hình nếu tự chạy thay vì script:
- SHARED_DB_USERNAME, SHARED_DB_PASSWORD: user riêng và password nhập an toàn.
- SHARED_DB_TRUSTSTORE_URL: file:///C:/.../aiven.p12 (URI, không phải đường dẫn Windows thô).
- SHARED_DB_TRUSTSTORE_PASSWORD: changeit cho truststore chỉ chứa CA công khai.
- SHARED_DB_HOST/PORT/NAME: mặc định service Aiven ở trên, không đổi sang localhost.
- SHARED_SERVER_PORT: mặc định 8082.
- GOOGLE_CLIENT_ID/SECRET và GEMINI_API_KEY/MODEL: chỉ environment server nếu cần tính năng này.
  Thiếu AI key không chặn website. Google callback local http://localhost:8082/login/oauth2/code/google,
  giữ callback HTTPS Render riêng trong cùng OAuth client.

Lệnh sau khi tự đặt env và tạo truststore:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=shared"

Không kết hợp shared với mysql/demo/prod/render. Không dùng ddl-auto=create/update hoặc bật seed.
Không cấp migrator credentials cho thành viên để chữa lỗi schema; báo người triển khai chạy migration.

## 3. Render dùng runtime user riêng, migration dùng deployment user riêng

Trong Render Environment:
- DB_USERNAME=san_nha_render; DB_PASSWORD nhập riêng.
- DB_URL giữ VERIFY_IDENTITY với /tmp/aiven-truststore.p12 như HOSTING_DEMO.md; Secret File aiven-ca.pem giữ nguyên.
- RUN_DB_MIGRATIONS=false mặc định. Sau schema hiện có, Render không cần quyền DDL để khởi động.
- Khi deploy có migration mới: quản trị viên backup DB trước, đặt RUN_DB_MIGRATIONS=true,
  MIGRATION_DB_USERNAME=san_nha_migrator và MIGRATION_DB_PASSWORD trong Environment rồi deploy.
  Flyway dùng credential riêng; Hibernate/app tiếp tục dùng san_nha_render.
- Sau migration thành công, đặt RUN_DB_MIGRATIONS=false và xóa MIGRATION_DB_* khỏi runtime rồi deploy lại.
- Không chia sẻ migration credentials. Guard từ chối migration nếu thiếu user riêng, trùng runtime hoặc dùng avnadmin.
- Bản cấu hình mới cần được đưa lên main/deploy sau khi chuẩn bị credentials. Không tự tạo/nâng cấp dịch vụ trả phí.

Quyền database mới là hàng rào DDL thực tế: app guard không thay thế GRANT tối thiểu.

## 4. Giữ dữ liệu local hiện có và chuyển an toàn

Backup local đã có trong backups/, không được Git theo dõi. Xem LOCAL_TO_AIVEN.md.
Chạy kiểm tra đích, password nhập ẩn:

    .\scripts\aiven-data.ps1 -Mode Inspect

Kết quả hiện biết ở local: 4 sân active, 5 accounts, 10 bookings, 4 ảnh sân, 4 price_rules.
Có một file QR không tham chiếu; giữ archive, không tự gán QR cho tài khoản.
Aiven chưa được truy vấn trực tiếp trong phiên chuẩn bị này.

Chỉ nếu mọi bảng nghiệp vụ đích trống, dừng local và tạm Suspend Render, rồi dùng Transfer trong tài liệu:
- snapshot/backup cả local và Aiven trước khi insert;
- giữ nguyên ID/FK của owner/court/giá/giờ/booking/chat;
- lưu ảnh gốc vào stored_uploads, kiểm tra hash trước commit;
- giữ nguyên flyway_schema_history của Aiven và tất cả migration đã chạy.

Nếu Aiven đã có account/sân/booking/chat/ảnh, công cụ dừng. Không import dump, không xóa đích để ép chạy.
Cần báo cáo counts/ID/email-conflict cho kế hoạch merge riêng; không thể kết luận hay remap an toàn khi chưa đọc đích.
Sau chuyển, mọi thành viên chuyển sang shared; mysql profile cũ chỉ dùng bảo trì/backup nguồn, không tiếp tục ghi song song.

## 5. Kiểm tra hai chiều thật (cần credentials và dữ liệu Aiven)

Chưa thực hiện được kiểm tra này vì thiếu phiên Aiven/member đã cấu hình. Không dùng H2 thay cho bằng chứng cloud.

1. Chạy shared bằng user riêng. /health local và https://san-nha-team.onrender.com/health đều phải 200 UP.
2. Đăng nhập khách hiện có tại local, nhắn đúng chủ sân một tin kiểm tra đồng bộ.
   Mở Render bằng chủ sân, refresh inbox: phải thấy tin và đúng hội thoại.
3. Trả lời từ Render, refresh lịch sử local: phải thấy tin của chủ sân.
4. Chỉnh một thông tin không nhạy cảm của sân từ local, đọc trang chi tiết Render không bộ lọc;
   chỉnh lại từ Render rồi kiểm tra local và phục hồi giá trị gốc. Không thay giá/QR nhận tiền chỉ để thử.
5. Đối chiếu ảnh sân đang có qua cùng đường dẫn /media/courts/<id>/<storedName> trên hai origin:
   bytes gốc hoặc SHA-256 phải giống nhau. QR yêu cầu login, chỉ kiểm tra với account có quyền.
6. Restart local và redeploy Render, kiểm tra sân/đơn/chat/ảnh vẫn có. Không xóa DB để reset demo.

Realtime SSE đang theo từng tiến trình: dữ liệu chat được lưu chung nhưng sự kiện từ local không tự broadcast qua Render
và ngược lại; refresh/history sẽ đọc dữ liệu chung. Muốn realtime xuyên nhiều instance cần thêm shared pub/sub/outbox,
không được giả định chỉ dùng chung MySQL là đã có event bus xuyên tiến trình.
Phiên/cookie và rate limit cũng tách theo instance; Google/customer permissions không đổi.

## Kiểm tra đã hoàn tất tại workspace

Maven verify: 115 tests đạt, BUILD SUCCESS. SharedDatabaseGuardTest nạp file profile shared thực tế và kiểm tra Aiven/TLS, HTTP cookie, Flyway off, chặn H2/localhost/seed/owner-create/mixed-profile/admin và tách user migration.
Script PowerShell qua parser; SQL generator đã kiểm tra không cấp DML flyway_schema_history cho runtime và không xuất password thật.
Chưa xác minh quyền user trên Aiven, chưa chuyển dữ liệu đích và chưa kiểm tra local–Render thật vì thiếu credentials; không dùng kết quả H2 làm bằng chứng cloud.
