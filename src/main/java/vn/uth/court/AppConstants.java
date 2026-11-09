package vn.uth.court;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppConstants {
  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  public static final List<String> SPORTS = List.of(
      "Cầu lông", "Bóng đá", "Tennis", "Bóng rổ",
      "Bóng chuyền", "Pickleball", "Bóng bàn");
  public static final List<String> AREAS = List.of(
      "Quận 1", "Quận 2", "Quận 3", "Quận 4",
      "Quận 5", "Quận 6", "Quận 7", "Quận 8",
      "Quận 9", "Quận 10", "Quận 11", "Quận 12",
      "Bình Thạnh", "Bình Tân", "Gò Vấp", "Phú Nhuận",
      "Tân Bình", "Tân Phú", "Thủ Đức",
      "Bình Chánh", "Cần Giờ", "Củ Chi", "Hóc Môn",
      "Nhà Bè", "Khác");
  public static final int MAX_BOOKING_DAYS = 30;
  public static final int CUSTOMER_CANCEL_HOURS = 2;
  public static final int DEPOSIT_PERCENT = 50;
  // Đơn chờ cọc chỉ giữ khung giờ trong khoảng này
  public static final int DEPOSIT_HOLD_MINUTES = 30;
  public static final List<String> ACTIVE_STATUSES =
      List.of("PENDING_DEPOSIT", "DEPOSIT_SENT", "CONFIRMED");
  // Mã ngân hàng theo VietQR, dùng cho ảnh QR chuyển khoản
  public static final Map<String, String> BANKS = new LinkedHashMap<>();
  public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
  public static final int MAX_IMAGES = 8;

  static {
    BANKS.put("VCB", "Vietcombank");
    BANKS.put("BIDV", "BIDV");
    BANKS.put("ICB", "VietinBank");
    BANKS.put("VBA", "Agribank");
    BANKS.put("MB", "MB Bank");
    BANKS.put("TCB", "Techcombank");
    BANKS.put("ACB", "ACB");
    BANKS.put("VPB", "VPBank");
    BANKS.put("TPB", "TPBank");
    BANKS.put("STB", "Sacombank");
    BANKS.put("VIB", "VIB");
    BANKS.put("OCB", "OCB");
  }

  private AppConstants() {}
}
