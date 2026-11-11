package vn.uth.court;

/** Public errors never contain upstream response bodies, prompts or credentials. */
public class AiUnavailableException extends RuntimeException {
  public enum Reason { CONNECTION, TIMEOUT, QUOTA, CONFIGURATION, BUSY, INCOMPLETE, INVALID_RESPONSE, BLOCKED }
  private final Reason reason;
  public AiUnavailableException() { this(Reason.CONNECTION); }
  public AiUnavailableException(Reason reason) { super(message(reason)); this.reason=reason; }
  public Reason reason() { return reason; }
  private static String message(Reason reason) {
    return switch(reason) {
      case TIMEOUT -> "Gemini phản hồi quá lâu. Bạn có thể gửi lại câu hỏi; tin nhắn chủ sân vẫn hoạt động.";
      case QUOTA -> "Gemini đang giới hạn lượt gọi hoặc đã hết quota. Vui lòng thử lại sau; bạn vẫn có thể tìm sân trực tiếp.";
      case CONFIGURATION -> "Cấu hình key hoặc model Gemini chưa được chấp nhận. Cần kiểm tra cấu hình AI trên máy chủ.";
      case BUSY -> "Trợ lý đang bận. Vui lòng thử lại sau ít phút.";
      case INCOMPLETE -> "Gemini chưa trả lời đầy đủ. Bạn có thể gửi lại hoặc rút gọn câu hỏi.";
      case INVALID_RESPONSE -> "Gemini trả về dữ liệu chưa hợp lệ. Bạn có thể gửi lại câu hỏi.";
      case BLOCKED -> "Gemini không xử lý được nội dung này. Hãy hỏi về sân, giá hoặc lịch trống.";
      default -> "Chưa kết nối được Gemini. Vui lòng thử lại; bạn vẫn có thể tìm sân và nhắn chủ sân.";
    };
  }
}
