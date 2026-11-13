package vn.uth.court;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourtConversationRepository extends JpaRepository<CourtConversation,Long> {
  long countByOwnerId(Long id);
  long countByCustomerId(Long id);
  Optional<CourtConversation> findByDirectKey(String directKey);
  Optional<CourtConversation> findByCustomerIdAndCourtId(Long customerId,Long courtId);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CourtConversation c where c.id=:id")
  Optional<CourtConversation> lock(@Param("id") Long id);
  @Query("select c from CourtConversation c where c.customer.id=:actor or c.owner.id=:actor order by c.updatedAt desc,c.id desc")
  Page<CourtConversation> inbox(@Param("actor") Long actor,Pageable page);
}
