package vn.uth.court;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AdminControlRepository extends JpaRepository<AdminControl,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AdminControl c where c.id=1")
 Optional<AdminControl> lockControl();
}
