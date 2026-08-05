package com.datapulse.backend.repository;

import com.datapulse.backend.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedFalseOrderByCreatedAtDesc();
    List<Alert> findBySourceIdOrderByCreatedAtDesc(String sourceId);
}
