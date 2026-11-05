package vn.uth.court;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtImageRepository extends JpaRepository<CourtImage, Long> {
  List<CourtImage> findByCourtIdOrderBySortOrderAsc(Long courtId);

  @Query(
      """
      select i from CourtImage i join fetch i.court
      where i.court.id in :courtIds
      order by i.sortOrder asc
      """)
  List<CourtImage> findByCourtIdInOrderBySortOrderAsc(@Param("courtIds") Collection<Long> courtIds);

  Optional<CourtImage> findByIdAndCourtId(Long id, Long courtId);

  long countByCourtId(Long courtId);
}
