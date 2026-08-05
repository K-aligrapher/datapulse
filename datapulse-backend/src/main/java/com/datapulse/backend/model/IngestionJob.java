package com.datapulse.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {
    @Id
    @Column(name = "job_id", length = 50)
    private String jobId;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "records_received", nullable = false)
    private Long recordsReceived = 0L;

    @Column(name = "records_processed", nullable = false)
    private Long recordsProcessed = 0L;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(nullable = false, length = 20)
    private String status; // RUNNING, COMPLETED, FAILED

    public IngestionJob() {}

    public IngestionJob(String jobId, String sourceId, Long recordsReceived, Long recordsProcessed, LocalDateTime startTime, String status) {
        this.jobId = jobId;
        this.sourceId = sourceId;
        this.recordsReceived = recordsReceived;
        this.recordsProcessed = recordsProcessed;
        this.startTime = startTime;
        this.status = status;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public Long getRecordsReceived() { return recordsReceived; }
    public void setRecordsReceived(Long recordsReceived) { this.recordsReceived = recordsReceived; }
    public Long getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(Long recordsProcessed) { this.recordsProcessed = recordsProcessed; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
