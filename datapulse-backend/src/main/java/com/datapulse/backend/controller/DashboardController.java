package com.datapulse.backend.controller;

import com.datapulse.backend.model.DataSource;
import com.datapulse.backend.model.QualityMetric;
import com.datapulse.backend.repository.AlertRepository;
import com.datapulse.backend.repository.DataSourceRepository;
import com.datapulse.backend.repository.IngestionJobRepository;
import com.datapulse.backend.repository.QualityMetricRepository;
import com.datapulse.backend.service.DataSimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final IngestionJobRepository jobRepository;
    private final QualityMetricRepository metricRepository;
    private final AlertRepository alertRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataSimulatorService simulatorService;

    public DashboardController(IngestionJobRepository jobRepository,
                               QualityMetricRepository metricRepository,
                               AlertRepository alertRepository,
                               DataSourceRepository dataSourceRepository,
                               DataSimulatorService simulatorService) {
        this.jobRepository = jobRepository;
        this.metricRepository = metricRepository;
        this.alertRepository = alertRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.simulatorService = simulatorService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview() {
        long totalJobs = jobRepository.count();
        long activeAlerts = alertRepository.findByResolvedFalseOrderByCreatedAtDesc().size();

        // Calculate average quality score from the latest metric of each data source
        List<DataSource> sources = dataSourceRepository.findAll();
        double sumScore = 0.0;
        int sourcesWithMetrics = 0;
        List<Map<String, Object>> sourceSummaries = new ArrayList<>();

        for (DataSource source : sources) {
            List<QualityMetric> latest = metricRepository.findLatestBySourceId(source.getSourceId(), 1);
            double score = 100.0;
            String status = "GREEN"; // Default health status

            if (!latest.isEmpty()) {
                QualityMetric m = latest.get(0);
                score = m.getQualityScore();
                sumScore += score;
                sourcesWithMetrics++;
                
                if (score < 80.0) {
                    status = "RED";
                } else if (score < 95.0) {
                    status = "YELLOW";
                }
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("sourceId", source.getSourceId());
            summary.put("name", source.getSourceName());
            summary.put("type", source.getSourceType());
            summary.put("owner", source.getOwner());
            summary.put("qualityScore", score);
            summary.put("status", status);
            sourceSummaries.add(summary);
        }

        double avgScore = sourcesWithMetrics > 0 ? (sumScore / sourcesWithMetrics) : 100.0;

        return ResponseEntity.ok(Map.of(
            "totalJobs", totalJobs,
            "activeAlerts", activeAlerts,
            "averageQualityScore", Math.round(avgScore * 10.0) / 10.0,
            "sources", sourceSummaries,
            "simulatorRunning", simulatorService.isRunning(),
            "induceAnomalies", simulatorService.isInduceAnomalies()
        ));
    }

    @PostMapping("/simulator/start")
    public ResponseEntity<?> startSimulator() {
        simulatorService.start();
        return ResponseEntity.ok(Map.of("message", "Simulator started successfully", "running", true));
    }

    @PostMapping("/simulator/stop")
    public ResponseEntity<?> stopSimulator() {
        simulatorService.stop();
        return ResponseEntity.ok(Map.of("message", "Simulator stopped successfully", "running", false));
    }

    @GetMapping("/simulator/status")
    public ResponseEntity<?> getSimulatorStatus() {
        return ResponseEntity.ok(Map.of(
            "running", simulatorService.isRunning(),
            "induceAnomalies", simulatorService.isInduceAnomalies()
        ));
    }

    @PostMapping("/simulator/anomalies")
    public ResponseEntity<?> setAnomalies(@RequestBody Map<String, Boolean> body) {
        Boolean induce = body.getOrDefault("induceAnomalies", false);
        simulatorService.setInduceAnomalies(induce);
        return ResponseEntity.ok(Map.of("induceAnomalies", induce));
    }
}
