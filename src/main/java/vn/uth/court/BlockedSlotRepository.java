package vn.uth.court;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {
  boolean existsBySlotKey(String key);

  Optional<BlockedSlot> findBySlotKey(String key);

  List<BlockedSlot> findByCourtIdAndPlayDate(Long courtId, LocalDate date);
}
