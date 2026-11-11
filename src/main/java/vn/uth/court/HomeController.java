package vn.uth.court;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

  private final CourtRepository courts;
  private final CourtImageRepository images;
  private final BookingService service;
  private final ReviewService reviews;

  public HomeController(
      CourtRepository courts,
      CourtImageRepository images,
      BookingService service,
      ReviewService reviews) {
    this.courts = courts;
    this.images = images;
    this.service = service;
    this.reviews = reviews;
  }

  @GetMapping("/")
  public String home(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String sport,
      @RequestParam(defaultValue = "") String area,
      @RequestParam(required = false) String minPrice,
      @RequestParam(required = false) String maxPrice,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) LocalDate date,
      Model model) {

    q = q.strip();
    sport = canonicalChoice(sport, AppConstants.SPORTS);
    List<String> areas = Stream.concat(AppConstants.AREAS.stream(), courts.findActiveAreas().stream())
        .distinct().sorted().toList();
    area = canonicalChoice(area, areas);
    if (date != null) service.checkDate(date);
    BigDecimal minimum = money(minPrice);
    BigDecimal maximum = money(maxPrice);

    if ((minimum != null && minimum.signum() < 0)
        || (maximum != null && maximum.signum() < 0)) {
      throw new IllegalArgumentException("Giá không được nhỏ hơn 0.");
    }

    if (minimum != null
        && maximum != null
        && minimum.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(
          "Giá từ không được lớn hơn giá đến.");
    }

    Page<Court> result =
        courts.search(
            q.trim(),
            sport,
            area,
            minimum,
            maximum,
            PageRequest.of(Math.max(page, 0), 8));

    if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
      result = courts.search(q, sport, area, minimum, maximum,
          PageRequest.of(result.getTotalPages() - 1, 8));
    }

    Map<String, Long> counts = sportCounts();

    Map<Long,Long> availability = new LinkedHashMap<>();
    if (date != null) for (Court court : result) availability.put(court.getId(),service.slots(court,date).stream().filter(slot -> "FREE".equals(slot.state())).count());
    model.addAttribute("selectedDate", date);
    model.addAttribute("today", service.today());
    model.addAttribute("areas", areas);
    model.addAttribute("searchLastDate",service.today().plusDays(AppConstants.MAX_BOOKING_DAYS));
    model.addAttribute("availability",availability);
    model.addAttribute("hasSearch", !q.isBlank() || !sport.isBlank() || !area.isBlank() || minimum != null || maximum != null || date != null);
    model.addAttribute("courts", result);
    model.addAttribute("resultFrom", result.isEmpty() ? 0L : result.getPageable().getOffset() + 1);
    model.addAttribute("resultTo", result.isEmpty() ? 0L : result.getPageable().getOffset() + result.getNumberOfElements());
    model.addAttribute("covers", covers(result.getContent()));
    model.addAttribute(
        "ratings",
        reviews.ratings(result.getContent().stream().map(Court::getId).toList()));

    // Danh sách môn và dữ liệu đếm sử dụng cùng một bộ khóa.
    model.addAttribute("sports", List.copyOf(counts.keySet()));
    model.addAttribute("sportCounts", counts);

    model.addAttribute("q", q);
    model.addAttribute("sport", sport);
    model.addAttribute("area", area);
    model.addAttribute("minPrice", minimum == null ? "" : minimum.toPlainString());
    model.addAttribute("maxPrice", maximum == null ? "" : maximum.toPlainString());
    model.addAttribute("searchReturnTo", CustomerNavigation.searchUrl(q, sport, area, date,
        minimum == null ? "" : minimum.toPlainString(), maximum == null ? "" : maximum.toPlainString(),
        result.getNumber()));

    return "home";
  }

  @GetMapping("/courts/{id}")
  public String detail(
      @PathVariable Long id,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "0") int reviewPage,
      @RequestParam(defaultValue = "/") String returnTo,
      Model model,
      Principal principal) {

    Court court = service.loadDetails(id);
    LocalDate selectedDate = date == null ? service.today() : date;

    model.addAttribute("court", court);
    model.addAttribute("searchReturnTo", CustomerNavigation.safeSearchUrl(returnTo));
    model.addAttribute("date", selectedDate);
    model.addAttribute("slots", service.slots(court, selectedDate));
    model.addAttribute(
        "lastDate",
        service.today().plusDays(AppConstants.MAX_BOOKING_DAYS));

    boolean isOwner =
        principal != null
            && court.getOwner().getEmail().equals(principal.getName());

    model.addAttribute("isOwner", isOwner);
    model.addAttribute("rating", reviews.rating(id));
    model.addAttribute("reviews", reviews.latest(id, reviewPage));

    return "detail";
  }

  private Map<Long, CourtImage> covers(List<Court> courtList) {
    Map<Long, CourtImage> result = new LinkedHashMap<>();

    List<Long> ids =
        courtList.stream()
            .map(Court::getId)
            .toList();

    if (ids.isEmpty()) {
      return result;
    }

    for (CourtImage image :
        images.findByCourtIdInOrderBySortOrderAsc(ids)) {
      result.putIfAbsent(image.getCourt().getId(), image);
    }

    return result;
  }

  private Map<String, Long> sportCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();

    // Đếm toàn bộ sân đang nhận đặt theo môn,
    // không chỉ các sân trong trang kết quả hiện tại.
    for (String sport : AppConstants.SPORTS) {
      long count = courts.countByActiveTrueAndSport(sport);
      counts.put(sport, count);
    }

    return counts;
  }

  private static BigDecimal money(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    try {
      return new BigDecimal(raw.trim());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Khoảng giá không hợp lệ.");
    }
  }

  private static String canonicalChoice(String raw, List<String> choices) {
    String value = raw.strip();
    return choices.stream().filter(choice -> choice.equalsIgnoreCase(value))
        .findFirst().orElse(value);
  }
}
