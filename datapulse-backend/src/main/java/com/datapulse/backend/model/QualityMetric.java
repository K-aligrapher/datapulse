package com.datapulse.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quality_metrics")
public class QualityMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(name = "missing_percentage", nullable = false)
    private Double missingPercentage = 0.0;

    @Column(name = "duplicate_percentage", nullable = false)
    private Double duplicatePercentage = 0.0;

    @Column(name = "invalid_percentage", nullable = false)
    private Double invalidPercentage = 0.0;

    @Column(name = "schema_changes", nullable = false)
    private Integer schemaChanges = 0;

    @Column(name = "outlier_count", nullable = false)
    private Integer outlierCount = 0;

    @Column(name = "quality_score", nullable = false)
    private Double qualityScore = 100.0;

    public QualityMetric() {}

    public QualityMetric(String sourceId, LocalDateTime timestamp, Double missingPercentage, Double duplicatePercentage, Double invalidPercentage, Integer schemaChanges, Integer outlierCount, Double qualityScore) {
        this.sourceId = sourceId;
        this.timestamp = timestamp;
        this.missingPercentage = missingPercentage;
        this.duplicatePercentage = duplicatePercentage;
        this.invalidPercentage = invalidPercentage;
        this.schemaChanges = schemaChanges;
        this.outlierCount = outlierCount;
        this.qualityScore = qualityScore;
    }

    public Long getMetricId() { return metricId; }
    public void setMetricId(Long metricId) { this.metricId = metricId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Double getMissingPercentage() { return missingPercentage; }
    public void setMissingPercentage(Double missingPercentage) { this.missingPercentage = missingPercentage; }
    public Double getDuplicatePercentage() { return duplicatePercentage; }
    public void setDuplicatePercentage(Double duplicatePercentage) { this.duplicatePercentage = duplicatePercentage; }
    public Double getInvalidPercentage() { return invalidPercentage; }
    public void setInvalidPercentage(Double invalidPercentage) { this.invalidPercentage = invalidPercentage; }
    public Integer getSchemaChanges() { return schemaChanges; }
    public void setSchemaChanges(Integer schemaChanges) { this.schemaChanges = schemaChanges; }
    public Integer getOutlierCount() { return outlierCount; }
    public void setOutlierCount(Integer outlierCount) { this.outlierCount = outlierCount; }
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
}
