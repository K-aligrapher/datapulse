package com.datapulse.backend.repository;

import com.datapulse.backend.model.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {
    List<ValidationRule> findBySourceId(String sourceId);
    List<ValidationRule> findBySourceIdAndEnabledTrue(String sourceId);
}
