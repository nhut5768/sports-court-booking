package vn.uth.court;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {
  List<PriceRule> findByCourtIdOrderByStartHourAsc(Long courtId);
}
