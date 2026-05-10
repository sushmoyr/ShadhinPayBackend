package com.shadhinpay.risk.repository;

import com.shadhinpay.risk.entity.RiskRule;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {

  Page<RiskRule> findByActiveTrueAndDeletedFalse(Pageable pageable);

  java.util.List<RiskRule> findByActiveTrueAndDeletedFalse();

  boolean existsByNameAndDeletedFalse(String name);
}
