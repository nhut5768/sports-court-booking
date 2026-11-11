package vn.uth.court;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookingController {
  private final BookingRepository bookings;
  private final AccountService accounts;
  private final BookingService service;
  private final ReviewService reviews;

  public BookingController(
      BookingRepository bookings,
      AccountService accounts,
      BookingService service,
      ReviewService reviews) {
    this.bookings = bookings;
    this.accounts = accounts;
    this.service = service;
    this.reviews = reviews;
  }

  @GetMapping("/bookings/confirm")
  String confirm(
      @RequestParam Long courtId,
      @RequestParam LocalDate date,
      @RequestParam int hour,
      @RequestParam(defaultValue = "/") String returnTo,
      Principal p,
      Model m) {
    Court c = service.loadDetails(courtId);
    Account customer = accounts.current(p.getName());
    if (!"CUSTOMER".equals(customer.getRole())) {
      throw new AccessDeniedException("Chỉ khách hàng được đặt sân.");
    }
    service.assertBookable(c, date, hour);
    BigDecimal price = service.priceFor(c, hour);
    m.addAttribute("court", c);
    m.addAttribute("searchReturnTo", CustomerNavigation.safeSearchUrl(returnTo));
    m.addAttribute("date", date);
    m.addAttribute("hour", hour);
    m.addAttribute("price", price);
    m.addAttribute("deposit", BookingService.depositFor(price));
    m.addAttribute("missingPhone", customer.getPhone() == null || customer.getPhone().isBlank());
    m.addAttribute("label", String.format("%02d:00 – %02d:00", hour, hour + 1));
    return "confirm";
  }

  @PostMapping("/bookings")
  String book(
      @RequestParam Long courtId,
      @RequestParam LocalDate date,
      @RequestParam int hour,
      @RequestParam(required = false) BigDecimal expectedPrice,
      @RequestParam(defaultValue = "/") String returnTo,
      Principal p,
      RedirectAttributes r) {
    try {
      Booking b = service.book(courtId, date, hour, accounts.current(p.getName()), expectedPrice);
      r.addFlashAttribute(
          "success",
          "Đã giữ khung giờ cho đơn #"
              + b.getId()
              + ". Xác nhận chắc chắn đến sân rồi chuyển cọc 50% để hoàn tất đặt sân.");
      return "redirect:/bookings/" + b.getId();
    } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException e) {
      r.addFlashAttribute(
          "error",
          e instanceof IllegalArgumentException
              ? e.getMessage()
              : "Khung giờ đã có người đặt. Vui lòng chọn lại.");
      return "redirect:" + CustomerNavigation.detailUrl(courtId, date, returnTo);
    }
  }

  @GetMapping("/bookings")
  String myBookings(Principal p, @RequestParam(defaultValue = "0") int page, Model m) {
    Account viewer = accounts.current(p.getName());
    m.addAttribute(
        "bookings",
        bookings.findByCustomerIdOrderByCreatedAtDesc(
            viewer.getId(), PageRequest.of(Math.max(page, 0), 10)));
    m.addAttribute("notifications", service.recentNotifications(viewer));
    return "bookings";
  }

  @PostMapping("/notifications/read")
  String readAll(Principal p) {
    service.markAllNotificationsRead(accounts.current(p.getName()));
    return "redirect:/bookings";
  }

  @GetMapping("/bookings/{id}")
  String detail(@PathVariable Long id, Principal p, Model m) {
    Account viewer = accounts.current(p.getName());
    Booking b = service.requireOwnedBooking(id, viewer);
    service.markNotificationsRead(viewer, id);
    m.addAttribute("booking", b);
    m.addAttribute("qrUrl", BookingService.depositQrUrl(b));
    m.addAttribute("bankName", AppConstants.BANKS.get(b.getCourt().getOwner().getBankCode()));
    m.addAttribute("review", reviews.forBooking(id).orElse(null));
    m.addAttribute("ownerView", false);
    return "booking-detail";
  }

  @PostMapping("/bookings/{id}/review")
  String review(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int rating,
      @RequestParam(required = false) String comment,
      Principal p,
      RedirectAttributes r) {
    try {
      reviews.save(id, accounts.current(p.getName()), rating, comment);
      r.addFlashAttribute("success", "Cảm ơn bạn đã đánh giá!");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/bookings/" + id + "#review";
  }

  @PostMapping("/bookings/{id}/commit")
  String commit(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      service.commit(id, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã xác nhận đến sân. Quét mã QR để chuyển cọc 50%.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/bookings/" + id;
  }

  @PostMapping("/bookings/{id}/deposit")
  String reportDeposit(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      service.reportDeposit(id, accounts.current(p.getName()));
      r.addFlashAttribute("success", "Đã báo chuyển cọc. Chủ sân sẽ kiểm tra và xác nhận.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/bookings/" + id;
  }

  @PostMapping("/bookings/{id}/cancel")
  String cancel(@PathVariable Long id, Principal p, RedirectAttributes r) {
    try {
      Booking b = service.cancel(id, accounts.current(p.getName()));
      r.addFlashAttribute(
          "success",
          b.getDepositForfeited()
              ? "Đã hủy đơn. Tiền cọc không được hoàn lại theo chính sách."
              : "Đã hủy đơn và trả lại khung giờ.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/bookings/" + id;
  }
}
