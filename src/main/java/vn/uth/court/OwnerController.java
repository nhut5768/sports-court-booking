package vn.uth.court;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerController {
  private final CourtRepository courts;
  private final BookingRepository bookings;
  private final AccountService accounts;
  private final BookingService service;
  private final ReviewService reviews;

  public OwnerController(
      CourtRepository courts,
      BookingRepository bookings,
      AccountService accounts,
      BookingService service,
      ReviewService reviews) {
    this.courts = courts;
    this.bookings = bookings;
    this.accounts = accounts;
    this.service = service;
    this.reviews = reviews;
  }

  @GetMapping("/owner")
  String owner(
      Principal p,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String courtId,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      Model m) {
    Account owner = accounts.current(p.getName());
    LocalDate start = from == null ? service.today().withDayOfMonth(1) : from;
    LocalDate end = to == null ? service.today() : to;
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
    }
    List<Court> owned = courts.findByOwnerIdOrderByIdDesc(owner.getId());
    Long selectedCourt = parseId(courtId);
    if (selectedCourt != null) {
      Court selected = service.requireCourt(selectedCourt);
      service.requireOwner(selected, owner);
    }
    BookingService.RevenueReport report =
        service.revenueReport(owner.getId(), start, end, selectedCourt);
    m.addAttribute("courts", owned);
    m.addAttribute(
        "bookings",
        bookings.searchForOwner(
            owner.getId(),
            status == null ? "" : status,
            selectedCourt,
            start,
            end,
            PageRequest.of(Math.max(page, 0), 10)));
    m.addAttribute("revenue", report.revenue());
    m.addAttribute("report", report);
    m.addAttribute("paidCount", report.completedCount());
    m.addAttribute(
        "pendingCount",
        bookings.countByCourtOwnerIdAndStatusIn(owner.getId(), AppConstants.ACTIVE_STATUSES));
    m.addAttribute("byCourt", report.byCourt());
    m.addAttribute("from", start);
    m.addAttribute("to", end);
    m.addAttribute("filterCourtId", selectedCourt);
    m.addAttribute("filterStatus", status);
    return "owner";
  }

  @GetMapping("/owner/courts/new")
  String newCourt(Model m) {
    m.addAttribute("form", new CourtForm());
    m.addAttribute("courtId", null);
    m.addAttribute("courtImages", List.of());
    return "court-form";
  }

  @GetMapping("/owner/courts/{id}/edit")
  String editCourt(@PathVariable Long id, Principal p, Model m) {
    Court c = service.loadDetails(id);
    service.requireOwner(c, accounts.current(p.getName()));
    m.addAttribute("form", new CourtForm(c));
    if(id!=null)service.requireOwner(service.requireCourt(id),accounts.current(p.getName()));
    m.addAttribute("courtId", id);
    m.addAttribute("courtImages", c.getImages());
    return "court-form";
  }

  @PostMapping("/owner/courts/save")
  String saveCourt(
      @RequestParam(required = false) Long id,
      @Valid @ModelAttribute("form") CourtForm f,
      BindingResult errors,
      @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
      Principal p,
      Model m,
      RedirectAttributes r) {
    if(id!=null)service.requireOwner(service.requireCourt(id),accounts.current(p.getName()));
    m.addAttribute("courtId", id);
    m.addAttribute(
        "courtImages", id == null ? List.of() : service.loadDetails(id).getImages());
    if (errors.hasErrors()) {
      return "court-form";
    }
    try {
      service.saveCourt(id, f, accounts.current(p.getName()), photos);
    } catch (IllegalArgumentException e) {
      errors.reject("court", e.getMessage());
      return "court-form";
    }
    r.addFlashAttribute("success", "Đã lưu thông tin sân.");
    return "redirect:/owner";
  }

  @PostMapping("/owner/courts/{id}/images/{imageId}/delete")
  String deleteImage(
      @PathVariable Long id,
      @PathVariable Long imageId,
      Principal p,
      RedirectAttributes r) {
    try {
      service.deleteImage(id, imageId, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã xóa ảnh.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/owner/courts/" + id + "/edit";
  }

  @PostMapping("/owner/courts/{id}/block")
  String block(
      @PathVariable Long id,
      @RequestParam LocalDate date,
      @RequestParam int hour,
      Principal p,
      RedirectAttributes r) {
    try {
      service.toggleBlock(id, date, hour, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã cập nhật trạng thái khung giờ.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/courts/" + id + "?date=" + date;
  }

  @PostMapping("/owner/bookings/{id}/complete")
  String complete(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      service.complete(id, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã ghi nhận hoàn tất và thu tiền.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/owner";
  }

  @PostMapping("/owner/bookings/{id}/deposit/confirm")
  String confirmDeposit(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      service.confirmDeposit(id, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã xác nhận nhận cọc cho đơn #" + id + ".");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/owner/bookings/" + id;
  }

  @PostMapping("/owner/bookings/{id}/deposit/reject")
  String rejectDeposit(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      service.rejectDeposit(id, accounts.current(p.getName()));
      r.addFlashAttribute(
          "success", "Đã báo khách chưa nhận được cọc. Đơn quay lại bước chờ đặt cọc.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/owner/bookings/" + id;
  }

  @PostMapping("/owner/reviews/{id}/reply")
  String replyReview(
      @PathVariable Long id,
      @RequestParam(required = false) String reply,
      Principal p,
      RedirectAttributes r) {
    Review review;
    try {
      review = reviews.reply(id, accounts.current(p.getName()), reply);
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
      return "redirect:/owner";
    }
    r.addFlashAttribute(
        "success", review.getOwnerReply() == null ? "Đã xóa phản hồi." : "Đã lưu phản hồi.");
    return "redirect:/courts/" + review.getCourt().getId() + "#review-" + id;
  }

  /** Khách quên mật khẩu: cấp mật khẩu tạm, hiển thị một lần để chủ sân đọc cho khách. */
  @PostMapping("/owner/bookings/{id}/temp-password")
  String tempPassword(@PathVariable Long id, Principal p, RedirectAttributes r) {
    Account owner = accounts.current(p.getName());
    Booking b = service.requireOwnedBooking(id, owner);
    String temp = accounts.issueTemporaryPassword(p.getName(), b.getCustomer().getId());
    r.addFlashAttribute("tempPassword", temp);
    r.addFlashAttribute(
        "success",
        "Đã cấp mật khẩu tạm cho " + b.getCustomer().getName() + ". Mật khẩu chỉ hiển thị một lần.");
    return "redirect:/owner/bookings/" + id;
  }

  @PostMapping("/owner/bookings/{id}/cancel")
  String ownerCancel(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      Booking b = service.cancel(id, accounts.current(p.getName()));
      r.addFlashAttribute(
          "success",
          b.depositPaid()
              ? "Đã hủy đơn. Khách đã chuyển cọc, hãy hoàn cọc cho khách."
              : "Đã hủy đơn.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/owner";
  }

  @GetMapping("/owner/bookings/{id}")
  String ownerBooking(@PathVariable Long id, Principal p, Model m) {
    Account owner = accounts.current(p.getName());
    Booking b = service.requireOwnedBooking(id, owner);
    if (!b.getCourt().getOwner().getId().equals(owner.getId())) {
      throw new org.springframework.security.access.AccessDeniedException("Không phải đơn của sân bạn.");
    }
    m.addAttribute("booking", b);
    m.addAttribute("review", reviews.forBooking(id).orElse(null));
    m.addAttribute("ownerView", true);
    return "booking-detail";
  }

  private static Long parseId(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Mã sân không hợp lệ.");
    }
  }
}
