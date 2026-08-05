package com.datapulse.backend.controller;

import com.datapulse.backend.model.Alert;
import com.datapulse.backend.repository.AlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<Alert> getUnresolvedAlerts() {
        return alertRepository.findByResolvedFalseOrderByCreatedAtDesc();
    }

    @GetMapping("/all")
    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<Alert> resolveAlert(@PathVariable("id") Long id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setResolved(true);
                    Alert updated = alertRepository.save(alert);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
