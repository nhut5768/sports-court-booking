package vn.uth.court;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
  public record Rating(double average, long total) {
    public String averageLabel() {
      return String.format(java.util.Locale.ROOT, "%.1f", average).replace('.', ',');
    }
  }

  private final ReviewRepository reviews;
  private final BookingRepository bookings;
  private final NotificationRepository notifications;
  private final Clock clock;

  public ReviewService(
      ReviewRepository reviews,
      BookingRepository bookings,
      NotificationRepository notifications,
      Clock clock) {
    this.reviews = reviews;
    this.bookings = bookings;
    this.notifications = notifications;
    this.clock = clock;
  }

  /** Chủ sân trả lời (hoặc sửa, xóa khi để trống) đánh giá về sân của mình; khách được thông báo. */
  @Transactional
  public Review reply(Long reviewId, Account owner, String text) {
    Review r =
        reviews.findById(reviewId).orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá."));
    if (!r.getCourt().getOwner().getId().equals(owner.getId())) {
      throw new AccessDeniedException("Bạn chỉ trả lời được đánh giá về sân của mình.");
    }
    String reply = text == null ? "" : text.trim();
    if (reply.length() > 1000) {
      throw new IllegalArgumentException("Phản hồi tối đa 1000 ký tự.");
    }
    boolean first = r.getOwnerReply() == null;
    r.setOwnerReply(reply.isEmpty() ? null : reply);
    r.setRepliedAt(reply.isEmpty() ? null : LocalDateTime.now(clock));
    reviews.saveAndFlush(r);
    if (first && !reply.isEmpty()) {
      Notification n = new Notification();
      n.setAccount(r.getCustomer());
      n.setBooking(r.getBooking());
      n.setMessage("Chủ sân " + r.getCourt().getName() + " đã trả lời đánh giá của bạn.");
      n.setCreatedAt(LocalDateTime.now(clock));
      notifications.save(n);
    }
    return r;
  }

  public Optional<Review> forBooking(Long bookingId) {
    return reviews.findByBookingId(bookingId);
  }

  /** Chỉ khách của đơn đã hoàn tất mới được đánh giá; gửi lại thì cập nhật đánh giá cũ. */
  @Transactional
  public Review save(Long bookingId, Account customer, int rating, String comment) {
    Booking b =
        bookings
            .findById(bookingId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn."));
    if (!b.getCustomer().getId().equals(customer.getId())) {
      throw new AccessDeniedException("Bạn chỉ đánh giá được đơn của mình.");
    }
    if (!"COMPLETED".equals(b.getStatus())) {
      throw new IllegalArgumentException("Chỉ đánh giá được sau khi lượt chơi đã hoàn tất.");
    }
    if (rating < 1 || rating > 5) {
      throw new IllegalArgumentException("Chọn từ 1 đến 5 sao.");
    }
    String text = comment == null ? "" : comment.trim();
    if (text.length() > 1000) {
      throw new IllegalArgumentException("Nhận xét tối đa 1000 ký tự.");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    Review r =
        reviews
            .findByBookingId(bookingId)
            .orElseGet(
                () -> {
                  Review n = new Review();
                  n.setBooking(b);
                  n.setCourt(b.getCourt());
                  n.setCustomer(b.getCustomer());
                  n.setCreatedAt(now);
                  return n;
                });
    r.setRating(rating);
    r.setComment(text.isEmpty() ? null : text);
    r.setUpdatedAt(now);
    return reviews.saveAndFlush(r);
  }

  public Page<Review> latest(Long courtId, int page) {
    return reviews.findByCourtIdOrderByCreatedAtDesc(courtId, PageRequest.of(Math.max(page, 0), 10));
  }

  public Rating rating(Long courtId) {
    return ratings(List.of(courtId)).get(courtId);
  }

  /** Sân chưa có đánh giá không nằm trong kết quả. */
  public Map<Long, Rating> ratings(List<Long> courtIds) {
    Map<Long, Rating> out = new HashMap<>();
    if (courtIds.isEmpty()) {
      return out;
    }
    for (ReviewRepository.Stats s : reviews.statsFor(courtIds)) {
      out.put(s.getCourtId(), new Rating(s.getAverage(), s.getTotal()));
    }
    return out;
  }
}
