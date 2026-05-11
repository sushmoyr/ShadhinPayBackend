package pay.conflux.backend.risk.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pay.conflux.backend.risk.entity.RiskRule;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {

  Page<RiskRule> findByActiveTrueAndDeletedFalse(Pageable pageable);

  java.util.List<RiskRule> findByActiveTrueAndDeletedFalse();

  boolean existsByNameAndDeletedFalse(String name);
}
