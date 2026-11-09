package vn.uth.court;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from Account a where a.id=:id")
  Optional<Account> lockForChat(@org.springframework.data.repository.query.Param("id") Long id);
  @org.springframework.data.jpa.repository.Query("select a from Account a where a.role='OWNER' and a.locked=false and exists (select c.id from Court c where c.owner.id=a.id and c.active=true) and (:q='' or lower(a.name) like lower(concat('%',:q,'%'))) order by a.name,a.id")
  org.springframework.data.domain.Page<Account> chatOwners(@org.springframework.data.repository.query.Param("q") String q,org.springframework.data.domain.Pageable page);
  long countByRole(String role);
  long countByLockedTrue();
  long countByRoleAndLockedFalse(String role);
  @org.springframework.data.jpa.repository.Query("select a from Account a where (:q='' or lower(a.name) like lower(concat('%',:q,'%')) or lower(a.email) like lower(concat('%',:q,'%')) or a.phone like concat('%',:q,'%')) and (:role='' or a.role=:role) and (:locked is null or a.locked=:locked) order by a.id desc")
  org.springframework.data.domain.Page<Account> adminSearch(
    @org.springframework.data.repository.query.Param("q") String q,
    @org.springframework.data.repository.query.Param("role") String role,
    @org.springframework.data.repository.query.Param("locked") Boolean locked,
    org.springframework.data.domain.Pageable page);
  Optional<Account> findByEmail(String email);
  Optional<Account> findByEmailIgnoreCase(String email);
  Optional<Account> findByGoogleSub(String googleSub);

  boolean existsByEmail(String email);

  Optional<Account> findByPhone(String phone);

  boolean existsByPhone(String phone);
}
