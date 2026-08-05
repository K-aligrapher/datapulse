package com.datapulse.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    private final LocalQualityEngineService localQualityEngineService;

    public IngestionService(LocalQualityEngineService localQualityEngineService) {
        this.localQualityEngineService = localQualityEngineService;
    }

    public void ingestEvent(String topic, String jsonPayload) throws Exception {
        // 1. Publish to Kafka if available
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(topic, jsonPayload);
                logger.info("Published message to Kafka topic: {}", topic);
            } catch (Exception e) {
                logger.error("Failed to publish message to Kafka: {}. Falling back to internal engine.", e.getMessage());
            }
        }

        // 2. Route to local simulated engine
        localQualityEngineService.processRecord(topic, jsonPayload);
    }

    public void ingestJsonArray(String topic, String jsonArrayPayload) throws Exception {
        List<Map<String, Object>> records = objectMapper.readValue(
            jsonArrayPayload, 
            new TypeReference<List<Map<String, Object>>>() {}
        );

        for (Map<String, Object> record : records) {
            String singlePayload = objectMapper.writeValueAsString(record);
            ingestEvent(topic, singlePayload);
        }
    }

    public void ingestCsv(String topic, String csvContent) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(csvContent));
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new IllegalArgumentException("CSV content is empty");
        }

        String[] headers = parseCsvLine(headerLine);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] values = parseCsvLine(line);
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim();
                String val = (i < values.length) ? values[i].trim() : "";
                
                // Try to parse numbers
                if (val.matches("-?\\d+")) {
                    record.put(header, Long.parseLong(val));
                } else if (val.matches("-?\\d*\\.\\d+")) {
                    record.put(header, Double.parseDouble(val));
                } else if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                    record.put(header, Boolean.parseBoolean(val));
                } else if (val.isEmpty()) {
                    record.put(header, null);
                } else {
                    record.put(header, val);
                }
            }
            String jsonPayload = objectMapper.writeValueAsString(record);
            ingestEvent(topic, jsonPayload);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString().replace("\"", ""));
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().replace("\"", ""));
        return values.toArray(new String[0]);
    }
}
