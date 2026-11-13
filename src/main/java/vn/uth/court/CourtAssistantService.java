package vn.uth.court;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CourtAssistantService {
  public record Link(String label, String url) {}
  public record Reply(String message, List<Link> links) {}
  private final CourtRepository courts;
  private final BookingService booking;
  public CourtAssistantService(CourtRepository courts, BookingService booking) {
    this.courts = courts; this.booking = booking;
  }
  public String today() { return booking.today().toString(); }
  public Reply answer(JsonNode intent) { return answer(intent,null); }
  public Reply answer(JsonNode intent,Long ownerId) {
    String action = text(intent,"action");
    if ("cancel_help".equals(action)) return new Reply(
        "Để hủy: đăng nhập → Lịch của tôi → mở đơn → chọn Hủy và xác nhận. Khách chỉ được hủy trước giờ chơi trên "
        + AppConstants.CUSTOMER_CANCEL_HOURS + " tiếng. Nếu khách hủy đơn đã trả cọc, tiền cọc bị giữ lại. "
        + "Trợ lý không tự hủy đơn hoặc xử lý hoàn tiền.", List.of(new Link("Mở lịch của tôi","/bookings")));
    if ("booking_help".equals(action)) return new Reply(
        "Chọn sân → chọn ngày và khung giờ còn trống → kiểm tra giá → xác nhận trên trang đặt sân. "
        + "Đặt cọc " + AppConstants.DEPOSIT_PERCENT + "% theo hướng dẫn, chủ sân xác nhận thanh toán. "
        + "Trợ lý chỉ hướng dẫn, không tự đặt sân hay thanh toán.", List.of(new Link("Tìm sân","/")));
    if (!"search".equals(action)) return new Reply(
        "Tôi hỗ trợ tìm sân, xem giá và lịch trống, hướng dẫn đặt hoặc hủy sân. Bạn muốn chơi môn gì, ở khu vực nào, ngày nào và lúc mấy giờ?",List.of());
    String sport = text(intent,"sport"), area = text(intent,"area"), dateText = text(intent,"date");
    List<String> missing = new ArrayList<>();
    if (!AppConstants.SPORTS.contains(sport)) missing.add("môn thể thao (" + String.join(", ",AppConstants.SPORTS) + ")");
    if (area == null || area.isBlank() || area.length() > 100) missing.add("khu vực muốn chơi");
    if (dateText == null || dateText.isBlank()) missing.add("ngày chơi");
    JsonNode hourNode = intent.path("hour");
    if (!hourNode.isIntegralNumber() || !hourNode.canConvertToInt() || hourNode.intValue() < 0 || hourNode.intValue() > 23)
      missing.add("giờ bắt đầu cụ thể, theo khung tròn giờ (ví dụ 19:00)");
    if (!missing.isEmpty()) return new Reply("Bạn cho tôi biết thêm " + String.join("; ",missing) + " nhé. Tôi cần đủ thông tin để kiểm tra lịch trống thực tế.",List.of());
    LocalDate date;
    try { date = LocalDate.parse(dateText); booking.checkDate(date); }
    catch (RuntimeException ex) { return new Reply("Vui lòng chọn ngày từ hôm nay đến " + AppConstants.MAX_BOOKING_DAYS + " ngày tới, theo dạng YYYY-MM-DD.",List.of()); }
    int hour = hourNode.intValue();
    BigDecimal min, max;
    try { min = money(intent,"minPrice"); max = money(intent,"maxPrice"); }
    catch (IllegalArgumentException ex) { return new Reply("Vui lòng nhập giá hợp lệ bằng VND mỗi giờ, không âm và tối đa 100 triệu.",List.of()); }
    if (min != null && max != null && min.compareTo(max) > 0)
      return new Reply("Giá tối thiểu không được lớn hơn giá tối đa. Bạn muốn khoảng giá nào?",List.of());
    // Same parameterized repository search as HomeController. No entity is sent to the AI.
    var page = ownerId==null?courts.search(area.trim(),sport,"",null,null,PageRequest.of(0,60)):
        courts.assistantOwned(ownerId,sport,area.trim(),PageRequest.of(0,60));
    List<Link> links = new ArrayList<>();
    StringBuilder result = new StringBuilder("Lịch trống ngày " + date + ", " + String.format("%02d:00",hour) + ":");
    for (Court court : page) {
      if(ownerId != null && !ownerId.equals(court.getOwner().getId())) continue;
      var free = booking.slots(court,date).stream().filter(slot -> slot.hour() == hour && "FREE".equals(slot.state()))
          .filter(slot -> min == null || slot.price().compareTo(min) >= 0)
          .filter(slot -> max == null || slot.price().compareTo(max) <= 0).findFirst();
      if (free.isEmpty()) continue;
      result.append("\n• ").append(court.getName()).append(" — ").append(court.getArea())
          .append(" — ").append(free.get().price().toPlainString()).append(" VND/giờ.");
      links.add(new Link("Xem " + court.getName(),"/courts/" + court.getId() + "?date=" + date));
      if (links.size() == 5) break;
    }
    if (links.isEmpty()) result = new StringBuilder("Không tìm thấy khung giờ còn trống phù hợp trong các sân đã kiểm tra. Bạn muốn đổi giờ, khu vực hoặc khoảng giá không?");
    result.append("\nKiểm tra tối đa 60 sân, hiển thị tối đa 5 kết quả. Lịch và giá có thể thay đổi; trang đặt sân sẽ kiểm tra lại trước khi xác nhận.");
    return new Reply(result.toString(),links);
  }
  private static String text(JsonNode node,String key) { return node.path(key).isTextual() ? node.path(key).textValue() : null; }
  private static BigDecimal money(JsonNode intent,String field) {
    JsonNode value = intent.path(field);
    if (value.isNull() || value.isMissingNode()) return null;
    if (!value.isIntegralNumber() || value.decimalValue().signum() < 0 || value.decimalValue().compareTo(new BigDecimal("100000000")) > 0)
      throw new IllegalArgumentException();
    return value.decimalValue();
  }
}
