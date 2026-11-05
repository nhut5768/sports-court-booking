package vn.uth.court;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Mỗi phút hủy các đơn chờ cọc đã quá hạn giữ chỗ để trả lại khung giờ. */
@Configuration
@EnableScheduling
public class DepositHoldJob {
  private static final Logger log = LoggerFactory.getLogger(DepositHoldJob.class);

  private final BookingService bookings;

  public DepositHoldJob(BookingService bookings) {
    this.bookings = bookings;
  }

  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  void expireHolds() {
    int count = bookings.expireHolds();
    if (count > 0) {
      log.info("Đã tự hủy {} đơn quá hạn chuyển cọc", count);
    }
  }
}
