package com.datapulse.backend.controller;

import com.datapulse.backend.model.QualityMetric;
import com.datapulse.backend.repository.QualityMetricRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final QualityMetricRepository metricRepository;

    public MetricsController(QualityMetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    @GetMapping
    public List<QualityMetric> getAllMetrics() {
        return metricRepository.findAll();
    }

    @GetMapping("/{sourceId}")
    public List<QualityMetric> getMetricsBySource(
            @PathVariable("sourceId") String sourceId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return metricRepository.findLatestBySourceId(sourceId, limit);
    }
}
