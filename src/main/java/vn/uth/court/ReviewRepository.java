package vn.uth.court;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  /** Điểm trung bình và số lượt đánh giá của một sân. */
  interface Stats {
    Long getCourtId();

    Double getAverage();

    long getTotal();
  }

  Optional<Review> findByBookingId(Long bookingId);

  Page<Review> findByCourtIdOrderByCreatedAtDesc(Long courtId, Pageable pageable);

  @Query(
      """
      select r.court.id as courtId, avg(r.rating) as average, count(r) as total
      from Review r where r.court.id in :courtIds group by r.court.id
      """)
  List<Stats> statsFor(@Param("courtIds") Collection<Long> courtIds);
}
