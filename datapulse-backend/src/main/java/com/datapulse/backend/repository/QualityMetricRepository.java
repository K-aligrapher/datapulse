package com.datapulse.backend.repository;

import com.datapulse.backend.model.QualityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface QualityMetricRepository extends JpaRepository<QualityMetric, Long> {
    List<QualityMetric> findBySourceIdOrderByTimestampDesc(String sourceId);
    
    @Query(value = "SELECT * FROM quality_metrics WHERE source_id = :sourceId ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<QualityMetric> findLatestBySourceId(@Param("sourceId") String sourceId, @Param("limit") int limit);
}
