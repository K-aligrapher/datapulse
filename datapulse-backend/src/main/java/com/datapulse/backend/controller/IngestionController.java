package com.datapulse.backend.controller;

import com.datapulse.backend.model.IngestionJob;
import com.datapulse.backend.repository.IngestionJobRepository;
import com.datapulse.backend.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);
    private final IngestionService ingestionService;
    private final IngestionJobRepository jobRepository;

    public IngestionController(IngestionService ingestionService, IngestionJobRepository jobRepository) {
        this.ingestionService = ingestionService;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/event")
    public ResponseEntity<?> ingestEvent(@RequestParam("topic") String topic, @RequestBody String payload) {
        String jobId = UUID.randomUUID().toString();
        IngestionJob job = new IngestionJob(jobId, topic, 1L, 1L, LocalDateTime.now(), "RUNNING");
        jobRepository.save(job);
        
        try {
            ingestionService.ingestEvent(topic, payload);
            job.setStatus("COMPLETED");
            job.setEndTime(LocalDateTime.now());
            jobRepository.save(job);
            return ResponseEntity.ok(Map.of("jobId", jobId, "status", "SUCCESS"));
        } catch (Exception e) {
            logger.error("Event ingestion failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setEndTime(LocalDateTime.now());
            jobRepository.save(job);
            return ResponseEntity.internalServerError().body(Map.of("jobId", jobId, "error", e.getMessage()));
        }
    }

    @PostMapping("/json")
    public ResponseEntity<?> ingestJsonArray(@RequestParam("topic") String topic, @RequestBody String payload) {
        String jobId = UUID.randomUUID().toString();
        IngestionJob job = new IngestionJob(jobId, topic, 0L, 0L, LocalDateTime.now(), "RUNNING");
        jobRepository.save(job);

        try {
            ingestionService.ingestJsonArray(topic, payload);
            job.setStatus("COMPLETED");
            job.setEndTime(LocalDateTime.now());
            // Approximation: We don't block just to parse again for count, but we can log success
            jobRepository.save(job);
            return ResponseEntity.ok(Map.of("jobId", jobId, "status", "SUCCESS"));
        } catch (Exception e) {
            logger.error("JSON array ingestion failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setEndTime(LocalDateTime.now());
            jobRepository.save(job);
            return ResponseEntity.internalServerError().body(Map.of("jobId", jobId, "error", e.getMessage()));
        }
    }

    @PostMapping("/csv")
    public ResponseEntity<?> ingestCsv(@RequestParam("topic") String topic, @RequestBody String csvContent) {
        String jobId = UUID.randomUUID().toString();
        IngestionJob job = new IngestionJob(jobId, topic, 0L, 0L, LocalDateTime.now(), "RUNNING");
        jobRepository.save(job);

        try {
            // Count lines roughly
            long lineCount = csvContent.split("\r\n|\r|\n").length - 1; // subtract header
            job.setRecordsReceived(Math.max(0, lineCount));
            
            ingestionService.ingestCsv(topic, csvContent);
            
            job.setStatus("COMPLETED");
            job.setRecordsProcessed(Math.max(0, lineCount));
            job.setEndTime(LocalDateTime.now());
            jobRepository.save(job);
            return ResponseEntity.ok(Map.of("jobId", jobId, "recordsIngested", lineCount, "status", "SUCCESS"));
        } catch (Exception e) {
            logger.error("CSV Ingestion failed for job {}", jobId, e);
            job.setStatus("FAILED");
            job.setEndTime(LocalDateTime.now());
            jobRepository.save(job);
            return ResponseEntity.internalServerError().body(Map.of("jobId", jobId, "error", e.getMessage()));
        }
    }
    
    @PostMapping("/csv-file")
    public ResponseEntity<?> ingestCsvFile(@RequestParam("topic") String topic, @RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes());
            return ingestCsv(topic, content);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
