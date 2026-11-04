package vn.uth.court;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtRepository extends JpaRepository<Court, Long> {
  long countByOwnerId(Long ownerId);
  @Query("select c from Court c where c.active=true and c.owner.id=:owner and c.sport=:sport and lower(c.area) like lower(concat('%',:area,'%')) order by c.id")
  Page<Court> assistantOwned(@Param("owner") Long owner,@Param("sport") String sport,@Param("area") String area,Pageable page);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Court c where c.id = :id")
  Optional<Court> lockById(@Param("id") Long id);

  List<Court> findByOwnerIdOrderByIdDesc(Long ownerId);

  long countByActiveTrueAndSport(String sport);

  @Query(
      """
      select c from Court c
      where c.active = true
        and (:q = '' or lower(c.name) like lower(concat('%', :q, '%'))
             or lower(c.address) like lower(concat('%', :q, '%'))
             or lower(c.area) like lower(concat('%', :q, '%')))
        and (:sport = '' or c.sport = :sport)
        and (:area = '' or c.area = :area)
        and (:minPrice is null or c.minPrice >= :minPrice)
        and (:maxPrice is null or c.minPrice <= :maxPrice)
      order by c.id
      """)
  Page<Court> search(
      @Param("q") String q,
      @Param("sport") String sport,
      @Param("area") String area,
      @Param("minPrice") BigDecimal minPrice,
      @Param("maxPrice") BigDecimal maxPrice,
      Pageable pageable);
}
