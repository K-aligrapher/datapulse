package com.datapulse.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "validation_rules")
public class ValidationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    private Double threshold;

    @Column(nullable = false, length = 20)
    private String severity; // WARNING, ERROR, CRITICAL

    @Column(nullable = false)
    private Boolean enabled = true;

    public ValidationRule() {}

    public ValidationRule(String sourceId, String ruleName, String ruleType, String fieldName, Double threshold, String severity, Boolean enabled) {
        this.sourceId = sourceId;
        this.ruleName = ruleName;
        this.ruleType = ruleType;
        this.fieldName = fieldName;
        this.threshold = threshold;
        this.severity = severity;
        this.enabled = enabled;
    }

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
