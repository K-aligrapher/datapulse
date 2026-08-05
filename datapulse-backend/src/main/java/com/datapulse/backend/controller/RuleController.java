package com.datapulse.backend.controller;

import com.datapulse.backend.model.ValidationRule;
import com.datapulse.backend.repository.ValidationRuleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private final ValidationRuleRepository ruleRepository;

    public RuleController(ValidationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @GetMapping
    public List<ValidationRule> getAllRules() {
        return ruleRepository.findAll();
    }

    @GetMapping("/{sourceId}")
    public List<ValidationRule> getRulesBySource(@PathVariable("sourceId") String sourceId) {
        return ruleRepository.findBySourceId(sourceId);
    }

    @PostMapping
    public ValidationRule createRule(@RequestBody ValidationRule rule) {
        return ruleRepository.save(rule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ValidationRule> updateRule(@PathVariable("id") Long id, @RequestBody ValidationRule ruleDetails) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setRuleName(ruleDetails.getRuleName());
                    rule.setRuleType(ruleDetails.getRuleType());
                    rule.setFieldName(ruleDetails.getFieldName());
                    rule.setThreshold(ruleDetails.getThreshold());
                    rule.setSeverity(ruleDetails.getSeverity());
                    rule.setEnabled(ruleDetails.getEnabled());
                    ValidationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable("id") Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    ruleRepository.delete(rule);
                    return ResponseEntity.ok(Map.of("message", "Rule deleted successfully"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ValidationRule> toggleRule(@PathVariable("id") Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setEnabled(!rule.getEnabled());
                    ValidationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
