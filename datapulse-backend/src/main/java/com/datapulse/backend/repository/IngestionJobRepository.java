package com.datapulse.backend.repository;

import com.datapulse.backend.model.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, String> {
    List<IngestionJob> findBySourceIdOrderByStartTimeDesc(String sourceId);
}
