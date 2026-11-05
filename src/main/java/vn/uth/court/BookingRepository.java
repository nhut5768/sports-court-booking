package vn.uth.court;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
  long countByCustomerId(Long id);
  @Query("select b.court.id from Booking b where b.id = :id")
  Optional<Long> courtIdFor(@Param("id") Long id);

  boolean existsByActiveSlotKey(String key);

  Optional<Booking> findByActiveSlotKey(String key);

  boolean existsByCustomerIdAndCourtOwnerId(Long customerId, Long ownerId);

  @Query(
      "select b.id from Booking b where b.status = 'PENDING_DEPOSIT' and b.holdUntil <= :now")
  List<Long> expiredHoldIds(@Param("now") java.time.LocalDateTime now);

  List<Booking> findByCourtIdAndPlayDate(Long courtId, LocalDate date);

  Page<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

  List<Booking> findByCourtOwnerIdOrderByCreatedAtDesc(Long ownerId);

  @Query(
      """
      select b from Booking b
      where b.court.owner.id = :ownerId
        and (:status = '' or b.status = :status)
        and (:courtId is null or b.court.id = :courtId)
        and (:fromDate is null or b.playDate >= :fromDate)
        and (:toDate is null or b.playDate <= :toDate)
      order by b.createdAt desc
      """)
  Page<Booking> searchForOwner(
      @Param("ownerId") Long ownerId,
      @Param("status") String status,
      @Param("courtId") Long courtId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      Pageable pageable);

  @Query(
      """
      select coalesce(sum(b.amount), 0) from Booking b
      where b.court.owner.id = :ownerId
        and b.status = 'COMPLETED'
        and b.playDate >= :fromDate and b.playDate <= :toDate
        and (:courtId is null or b.court.id = :courtId)
      """)
  BigDecimal revenue(
      @Param("ownerId") Long ownerId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      @Param("courtId") Long courtId);

  @Query(
      """
      select b from Booking b
      where b.court.owner.id = :ownerId
        and b.status = 'COMPLETED'
        and b.playDate >= :fromDate and b.playDate <= :toDate
      """)
  List<Booking> completedForOwner(
      @Param("ownerId") Long ownerId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  long countByCourtOwnerIdAndStatusAndPlayDateBetween(
      Long ownerId, String status, LocalDate from, LocalDate to);

  long countByCourtOwnerIdAndStatus(Long ownerId, String status);

  /** Đơn có tiền đã về tay chủ sân: hoàn tất hoặc đã xác nhận nhận cọc. */
  @Query(
      """
      select b from Booking b
      where b.court.owner.id = :ownerId
        and b.playDate >= :fromDate and b.playDate <= :toDate
        and (b.status = 'COMPLETED' or b.depositConfirmedAt is not null)
      order by b.playDate asc, b.startHour asc
      """)
  List<Booking> moneyForOwner(
      @Param("ownerId") Long ownerId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query(
      """
      select b from Booking b
      where b.court.owner.id = :ownerId
        and b.status = 'CONFIRMED' and b.depositConfirmedAt is not null
      """)
  List<Booking> heldDepositsForOwner(@Param("ownerId") Long ownerId);

  long countByCourtOwnerIdAndStatusIn(Long ownerId, Collection<String> statuses);
}
