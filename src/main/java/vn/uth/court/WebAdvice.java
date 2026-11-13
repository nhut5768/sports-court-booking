package vn.uth.court;

import java.security.Principal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebAdvice {
  private final AccountService accounts;
  private final CourtMessagingService chat;
  private final BookingService bookings;
  private final BookingRepository bookingRepo;
  private final NotificationRepository notifications;

  public WebAdvice(
      AccountService accounts, CourtMessagingService chat,
      BookingService bookings,
      BookingRepository bookingRepo,
      NotificationRepository notifications) {
    this.accounts = accounts;
    this.chat = chat;
    this.bookings = bookings;
    this.bookingRepo = bookingRepo;
    this.notifications = notifications;
  }

  @ModelAttribute
  public void common(Model m, Principal p) {
    m.addAttribute("today", bookings.today());
    m.addAttribute("sports", AppConstants.SPORTS);
    m.addAttribute("areas", AppConstants.AREAS);
    if (p != null) {
      Account viewer = accounts.current(p.getName());
      m.addAttribute("viewer", viewer);
      m.addAttribute("chatUnreadCount", viewer.isAdmin() ? 0L : chat.totalUnread(p.getName()));
      m.addAttribute("unreadNotifications", notifications.countByAccountIdAndReadAtIsNull(viewer.getId()));
      if (viewer.isOwner()) {
        // Số đơn khách báo đã chuyển cọc, chờ chủ sân xác nhận
        m.addAttribute(
            "depositAlerts",
            bookingRepo.countByCourtOwnerIdAndStatus(viewer.getId(), "DEPOSIT_SENT"));
      }
    }
  }
}
