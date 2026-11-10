package vn.uth.court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface AdminAuditRepository extends JpaRepository<AdminAudit,Long> {
 Page<AdminAudit> findAllByOrderByIdDesc(Pageable page);
 Page<AdminAudit> findByTargetIdOrderByIdDesc(Long target,Pageable page);
}
