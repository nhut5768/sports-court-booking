package vn.uth.court;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  long countByAccountIdAndReadAtIsNull(Long accountId);

  @Query(
      """
      select n from Notification n left join fetch n.booking
      where n.account.id = :accountId
      order by n.createdAt desc, n.id desc
      limit 10
      """)
  List<Notification> recentFor(@Param("accountId") Long accountId);

  @Modifying
  @Query(
      """
      update Notification n set n.readAt = :now
      where n.account.id = :accountId and n.booking.id = :bookingId and n.readAt is null
      """)
  int markRead(
      @Param("accountId") Long accountId,
      @Param("bookingId") Long bookingId,
      @Param("now") LocalDateTime now);

  @Modifying
  @Query(
      "update Notification n set n.readAt = :now where n.account.id = :accountId and n.readAt is null")
  int markAllRead(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);
}
