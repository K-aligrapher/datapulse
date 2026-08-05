package com.datapulse.backend.service;

import com.datapulse.backend.model.*;
import com.datapulse.backend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class LocalQualityEngineService {

    private static final Logger logger = LoggerFactory.getLogger(LocalQualityEngineService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ValidationRuleRepository ruleRepository;
    private final QualityMetricRepository metricRepository;
    private final AlertRepository alertRepository;
    private final WebSocketService webSocketService;

    // Buffers for streaming micro-batches
    private final Map<String, List<Map<String, Object>>> batchBuffers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenIds = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> historicalValues = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> establishedSchemas = new ConcurrentHashMap<>();
    
    // Live records tracking
    private final Map<String, AtomicLong> recordCountSinceLastSec = new ConcurrentHashMap<>();
    private long lastCheckTime = System.currentTimeMillis();

    // Regex compilation
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

    public LocalQualityEngineService(ValidationRuleRepository ruleRepository,
                                     QualityMetricRepository metricRepository,
                                     AlertRepository alertRepository,
                                     WebSocketService webSocketService) {
        this.ruleRepository = ruleRepository;
        this.metricRepository = metricRepository;
        this.alertRepository = alertRepository;
        this.webSocketService = webSocketService;
        
        // Seed schema signatures for change detection
        establishedSchemas.put("sales-data", new HashSet<>(Arrays.asList("transactionId", "customerId", "productId", "amount", "timestamp")));
        establishedSchemas.put("customer-data", new HashSet<>(Arrays.asList("customerId", "name", "email", "phone", "timestamp")));
        establishedSchemas.put("sensor-data", new HashSet<>(Arrays.asList("sensorId", "temperature", "humidity", "timestamp")));
        establishedSchemas.put("payment-data", new HashSet<>(Arrays.asList("paymentId", "orderId", "amount", "paymentMethod", "timestamp")));
        establishedSchemas.put("inventory-data", new HashSet<>(Arrays.asList("productId", "quantity", "warehouseId", "timestamp")));
    }

    public void processRecord(String topic, String jsonPayload) {
        try {
            Map<String, Object> record = objectMapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});
            batchBuffers.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(record);
            recordCountSinceLastSec.computeIfAbsent(topic, k -> new AtomicLong(0)).incrementAndGet();
            
            // Save Raw Data (Simulation of HDFS raw)
            saveRawRecordToFile(topic, jsonPayload);
        } catch (Exception e) {
            logger.error("Failed to parse record for topic {}: {}", topic, e.getMessage());
            logRawFormatError(topic, jsonPayload);
        }
    }

    private void logRawFormatError(String topic, String rawPayload) {
        Alert alert = new Alert(topic, "CRITICAL", "Corrupted raw event payload: " + rawPayload, false);
        alertRepository.save(alert);
        webSocketService.broadcastAlert(alert);
    }

    @Scheduled(fixedRate = 5000)
    public void processMicroBatch() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastCheckTime) / 1000.0;
        lastCheckTime = now;

        for (String topic : establishedSchemas.keySet()) {
            List<Map<String, Object>> batch = batchBuffers.get(topic);
            List<Map<String, Object>> recordsToProcess = new ArrayList<>();
            if (batch != null) {
                synchronized (batch) {
                    recordsToProcess.addAll(batch);
                    batch.clear();
                }
            }

            int totalRecords = recordsToProcess.size();
            long inputRate = Math.round(totalRecords / (elapsedSeconds > 0 ? elapsedSeconds : 5.0));

            // Publish processing rates to dashboard
            webSocketService.broadcastThroughput(topic, inputRate, totalRecords);

            if (totalRecords == 0) {
                continue; // Skip evaluation if no new data arrived
            }

            evaluateBatch(topic, recordsToProcess, totalRecords);
        }
    }

    private void evaluateBatch(String topic, List<Map<String, Object>> records, int totalRecords) {
        // Load active validation rules
        List<ValidationRule> rules = ruleRepository.findBySourceIdAndEnabledTrue(topic);

        int missingCount = 0;
        int invalidCount = 0;
        int duplicateCount = 0;
        int outlierCount = 0;
        int schemaChanges = 0;

        List<Double> batchValuesForDrift = new ArrayList<>();
        String primaryKeyField = getPrimaryKeyField(topic);
        Set<String> batchSeenIds = seenIds.computeIfAbsent(topic, k -> Collections.synchronizedSet(new HashSet<>()));

        List<Map<String, Object>> validRecords = new ArrayList<>();
        List<Map<String, Object>> invalidRecordsList = new ArrayList<>();

        for (Map<String, Object> record : records) {
            boolean isRecordValid = true;

            // 1. Schema check
            Set<String> recordFields = record.keySet();
            Set<String> expectedFields = establishedSchemas.get(topic);
            if (expectedFields != null && !recordFields.containsAll(expectedFields)) {
                schemaChanges++;
                isRecordValid = false;
            }

            // 2. Evaluate rules
            for (ValidationRule rule : rules) {
                String field = rule.getFieldName();
                Object val = record.get(field);

                switch (rule.getRuleType()) {
                    case "REQUIRED_FIELD" -> {
                        if (val == null || val.toString().trim().isEmpty()) {
                            missingCount++;
                            isRecordValid = false;
                            triggerRuleAlert(topic, rule, "Missing value in required field: " + field);
                        }
                    }
                    case "INVALID_TYPE" -> {
                        if (val != null && field.equals("amount") && !(val instanceof Number)) {
                            invalidCount++;
                            isRecordValid = false;
                            triggerRuleAlert(topic, rule, "Invalid value type in field: " + field);
                        }
                    }
                    case "INVALID_EMAIL" -> {
                        if (val != null && !EMAIL_PATTERN.matcher(val.toString()).matches()) {
                            invalidCount++;
                            isRecordValid = false;
                            triggerRuleAlert(topic, rule, "Invalid email format: " + val);
                        }
                    }
                    case "INVALID_PHONE" -> {
                        if (val != null && !PHONE_PATTERN.matcher(val.toString()).matches()) {
                            invalidCount++;
                            isRecordValid = false;
                            triggerRuleAlert(topic, rule, "Invalid 10-digit phone format: " + val);
                        }
                    }
                    case "CUSTOM_CHECK" -> {
                        if (val instanceof Number && rule.getThreshold() != null) {
                            double numericVal = ((Number) val).doubleValue();
                            if (numericVal < rule.getThreshold()) {
                                invalidCount++;
                                isRecordValid = false;
                                triggerRuleAlert(topic, rule, "Threshold breach on: " + field + " (" + numericVal + " < " + rule.getThreshold() + ")");
                            }
                        }
                    }
                    case "OUTLIER" -> {
                        if (val instanceof Number) {
                            double numVal = ((Number) val).doubleValue();
                            batchValuesForDrift.add(numVal);
                            
                            // Check z-score against historical values
                            List<Double> hist = historicalValues.computeIfAbsent(topic + "." + field, k -> new ArrayList<>());
                            if (hist.size() > 10) {
                                double mean = calcMean(hist);
                                double std = calcStdDev(hist, mean);
                                if (std > 0) {
                                    double z = Math.abs(numVal - mean) / std;
                                    double zThreshold = rule.getThreshold() != null ? rule.getThreshold() : 3.0;
                                    if (z > zThreshold) {
                                        outlierCount++;
                                        triggerRuleAlert(topic, rule, "Outlier value detected: " + field + "=" + numVal + " (Z-Score: " + String.format("%.2f", z) + ")");
                                    }
                                }
                            }
                            hist.add(numVal);
                            if (hist.size() > 500) hist.remove(0); // keep sliding window
                        }
                    }
                }
            }

            // 3. Duplicate check
            if (primaryKeyField != null && record.containsKey(primaryKeyField)) {
                String idVal = String.valueOf(record.get(primaryKeyField));
                if (batchSeenIds.contains(idVal)) {
                    duplicateCount++;
                    isRecordValid = false;
                } else {
                    batchSeenIds.add(idVal);
                    // Avoid memory leaks
                    if (batchSeenIds.size() > 2000) {
                        batchSeenIds.iterator().next(); // remove arbitrary oldest
                    }
                }
            }

            if (isRecordValid) {
                validRecords.add(record);
            } else {
                invalidRecordsList.add(record);
            }
        }

        // 4. Calculate drift on numerical properties
        for (ValidationRule rule : rules) {
            if ("DRIFT".equals(rule.getRuleType()) && !batchValuesForDrift.isEmpty()) {
                String field = rule.getFieldName();
                double batchMean = calcMean(batchValuesForDrift);
                List<Double> hist = historicalValues.get(topic + "." + field);
                if (hist != null && hist.size() > 30) {
                    double histMean = calcMean(hist);
                    double driftPct = Math.abs(batchMean - histMean) / histMean;
                    double driftThreshold = rule.getThreshold() != null ? rule.getThreshold() : 0.2;
                    if (driftPct > driftThreshold) {
                        triggerRuleAlert(topic, rule, "Significant distribution drift detected in: " + field + " (" + String.format("%.1f", driftPct * 100) + "% shift)");
                    }
                }
            }
        }

        // 5. Calculate Metrics Percentages
        double missingPct = ((double) missingCount / totalRecords) * 100.0;
        double duplicatePct = ((double) duplicateCount / totalRecords) * 100.0;
        double invalidPct = ((double) invalidCount / totalRecords) * 100.0;

        // Weights: Missing (30%), Duplicates (20%), Schema Errors (20% is schemaChanges > 0), Invalid Formats (15%), Outliers (15%)
        double schemaPenalty = schemaChanges > 0 ? 20.0 : 0.0;
        double outlierPenalty = outlierCount > 0 ? 15.0 : 0.0;
        double qualityScore = 100.0 - (missingPct * 0.3 + duplicatePct * 0.2 + schemaPenalty + invalidPct * 0.15 + outlierPenalty);
        qualityScore = Math.max(0.0, Math.min(100.0, qualityScore));

        // Save quality metrics
        QualityMetric metric = new QualityMetric(
                topic,
                LocalDateTime.now(),
                missingPct,
                duplicatePct,
                invalidPct,
                schemaChanges,
                outlierCount,
                qualityScore
        );
        metricRepository.save(metric);
        webSocketService.broadcastMetric(metric);

        // Save records to local storage files
        saveProcessedRecords(topic, validRecords);
        saveDeadLetterRecords(topic, invalidRecordsList);
    }

    private void triggerRuleAlert(String topic, ValidationRule rule, String details) {
        Alert alert = new Alert(topic, rule.getSeverity(), "[" + rule.getRuleName() + "] " + details, false);
        alertRepository.save(alert);
        webSocketService.broadcastAlert(alert);
    }

    private String getPrimaryKeyField(String topic) {
        return switch (topic) {
            case "sales-data" -> "transactionId";
            case "customer-data" -> "customerId";
            case "sensor-data" -> "sensorId";
            case "payment-data" -> "paymentId";
            case "inventory-data" -> "productId";
            default -> null;
        };
    }

    private double calcMean(List<Double> vals) {
        double sum = 0;
        for (double v : vals) sum += v;
        return vals.isEmpty() ? 0 : sum / vals.size();
    }

    private double calcStdDev(List<Double> vals, double mean) {
        double sqSum = 0;
        for (double v : vals) sqSum += Math.pow(v - mean, 2);
        return vals.size() <= 1 ? 0 : Math.sqrt(sqSum / (vals.size() - 1));
    }

    // Storage simulation
    private void saveRawRecordToFile(String topic, String recordJson) {
        writeRecordToFile("storage/raw/" + topic + ".json", recordJson);
    }

    private void saveProcessedRecords(String topic, List<Map<String, Object>> records) {
        for (Map<String, Object> rec : records) {
            try {
                writeRecordToFile("storage/processed/" + topic + ".json", objectMapper.writeValueAsString(rec));
            } catch (Exception e) {
                logger.error("HDFS processed storage simulation failed", e);
            }
        }
    }

    private void saveDeadLetterRecords(String topic, List<Map<String, Object>> records) {
        for (Map<String, Object> rec : records) {
            try {
                writeRecordToFile("storage/dead-letter/" + topic + ".json", objectMapper.writeValueAsString(rec));
            } catch (Exception e) {
                logger.error("HDFS dead-letter storage simulation failed", e);
            }
        }
    }

    private synchronized void writeRecordToFile(String relativePath, String data) {
        try {
            File file = new File(relativePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(data + "\n");
            }
        } catch (Exception e) {
            logger.error("Failed to write to file: {}", relativePath, e);
        }
    }
}
