# Chạy localhost:8082

Chạy tại thư mục dự án:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-local.ps1

Script build trước khi nhận secret; dùng MySQL local sports_court_booking, bind 127.0.0.1:8082. Không seed hay xóa dữ liệu. Không deploy.
Tái dùng biến môi trường Process/User: DB_USERNAME, DB_PASSWORD, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GEMINI_API_KEY, GEMINI_MODEL. Thiếu secret thì hỏi bằng Read-Host -AsSecureString; không ghi secret ra file, Git hoặc log.

Google cần cả Client ID và Secret. Callback Web OAuth phải đăng ký chính xác http://localhost:8082/login/oauth2/code/google. Script kiểm tra nút và redirect; người dùng hoàn tất chọn tài khoản trong trình duyệt để xác minh credentials.

Gemini không có tên model mặc định. Script gọi models.list bằng key, chỉ cho chọn model hỗ trợ generateContent trong kết quả thực; tái dùng GEMINI_MODEL khi có trong danh sách. Sau đó gọi generateContent thực tế. Lỗi key/quota/model/network chỉ tắt AI, không dừng website hoặc nhắn chủ sân. API: https://ai.google.dev/api/models và https://ai.google.dev/api/generate-content.

Dùng -Restart nếu cần dừng JAR Sân Nhà local đã chạy; không dừng tiến trình khác. -SkipBuild chỉ dùng khi JAR đã được build mới. Ctrl+C dừng Java do script tạo.

Kiểm tra: đăng nhập Google hai lần cùng tài khoản; khóa tài khoản rồi thử lại; bấm Nhắn tin chủ sân tại trang sân, mở mục Tin nhắn của chủ sân bằng trình duyệt khác; gửi hai chiều và tải lại để kiểm tra lịch sử. Hỏi AI trong mục riêng và đối chiếu sân/giá/lịch trống.

Kiểm tra môi trường ngày 2026-11-02: không có các biến credentials nêu trên trong Process/User; chưa xác minh đăng nhập Google hoặc Gemini thật bằng key của người dùng. Kiểm thử tự động dùng H2 riêng và credentials giả, không thay thế kiểm chứng credentials thật.

## Remember credentials on this Windows account

The launcher now saves configuration to Windows Credential Manager (Generic Credential: SanNha/localhost8082) after a healthy startup. Environment variables override remembered values. Secrets are never written to project/config files. Enter credentials once on the first run with this version; subsequent launches reuse them, including the verified model. Keep the launcher running for the website to remain available; powering off or sleeping the PC stops availability.

- Clear saved configuration without stopping the website: powershell.exe -NoProfile -File .\scripts\run-local.ps1 -ForgetCredentials
- Run without reading or writing the vault: add -NoRemember
- Restart using saved configuration: powershell.exe -NoProfile -File .\scripts\run-local.ps1 -Restart

Windows vault behavior: https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew
