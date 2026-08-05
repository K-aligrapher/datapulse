package com.datapulse.spark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.*;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

public class SparkStreamingApp {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{10}$");

    public static void main(String[] args) {
        String kafkaBootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/datapulse");
        String dbUser = System.getenv().getOrDefault("DB_USER", "datapulse");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "password");

        System.out.println("Starting Spark Observability streaming pipeline...");
        System.out.println("Connecting to Kafka: " + kafkaBootstrap);
        System.out.println("Connecting to Database: " + dbUrl);

        SparkSession spark = SparkSession.builder()
                .appName("DataPulseSparkProcessor")
                .master("local[*]") // Run in local multi-core mode
                .config("spark.sql.streaming.forceDeleteTempCheckpointLocation", "true")
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");

        // Subscribe to Kafka topics
        Dataset<Row> kafkaStream = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", kafkaBootstrap)
                .option("subscribe", "sales-data,customer-data,sensor-data,payment-data,inventory-data")
                .option("startingOffsets", "latest")
                .load();

        // Select key, value and topic columns
        Dataset<Row> recordsStream = kafkaStream.selectExpr(
                "CAST(topic AS STRING) as topic",
                "CAST(value AS STRING) as payload"
        );

        // Process batches using foreachBatch to connect Spark with our PostgreSQL rule configuration
        StreamingQuery query = recordsStream.writeStream()
                .foreachBatch((Dataset<Row> batchDf, Long batchId) -> {
                    long count = batchDf.count();
                    if (count == 0) return;

                    System.out.println("Spark processing batch " + batchId + " containing " + count + " events...");
                    List<Row> rows = batchDf.collectAsList();

                    // Group rows by topic
                    Map<String, List<String>> payloadsByTopic = new HashMap<>();
                    for (Row r : rows) {
                        String topic = r.getString(0);
                        String payload = r.getString(1);
                        payloadsByTopic.computeIfAbsent(topic, k -> new ArrayList<>()).add(payload);
                    }

                    // Process each topic within the micro-batch
                    for (Map.Entry<String, List<String>> entry : payloadsByTopic.entrySet()) {
                        processTopicBatch(entry.getKey(), entry.getValue(), dbUrl, dbUser, dbPassword);
                    }
                })
                .trigger(Trigger.ProcessingTime("5 seconds"))
                .start();

        try {
            query.awaitTermination();
        } catch (Exception e) {
            System.err.println("Spark Streaming Query encountered a terminal failure: " + e.getMessage());
        }
    }

    private static void processTopicBatch(String topic, List<String> payloads, String dbUrl, String dbUser, String dbPassword) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            
            // 1. Fetch rules for this topic
            List<Map<String, Object>> rules = fetchRules(conn, topic);

            int totalRecords = payloads.size();
            int missingCount = 0;
            int invalidCount = 0;
            int duplicateCount = 0;
            int outlierCount = 0;
            int schemaChanges = 0;

            Set<String> expectedFields = getExpectedSchema(topic);
            String primaryKeyField = getPrimaryKeyField(topic);
            Set<String> seenIds = new HashSet<>();

            List<Map<String, Object>> validRecords = new ArrayList<>();
            List<Map<String, Object>> invalidRecords = new ArrayList<>();

            for (String payload : payloads) {
                boolean isRecordValid = true;
                Map<String, Object> record;
                try {
                    record = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    // Record format corruption
                    isRecordValid = false;
                    invalidCount++;
                    saveRawRecordToFile("storage/spark-dead-letter/" + topic + ".json", payload);
                    insertAlert(conn, topic, "CRITICAL", "Corrupted raw event payload in Spark batch: " + payload);
                    continue;
                }

                // A. Check schema mismatch
                Set<String> recordFields = record.keySet();
                if (expectedFields != null && !recordFields.containsAll(expectedFields)) {
                    schemaChanges++;
                    isRecordValid = false;
                }

                // B. Run rules validation
                for (Map<String, Object> rule : rules) {
                    String type = (String) rule.get("rule_type");
                    String field = (String) rule.get("field_name");
                    String ruleName = (String) rule.get("rule_name");
                    String severity = (String) rule.get("severity");
                    Double threshold = (Double) rule.get("threshold");
                    Object val = record.get(field);

                    if ("REQUIRED_FIELD".equals(type)) {
                        if (val == null || val.toString().trim().isEmpty()) {
                            missingCount++;
                            isRecordValid = false;
                            insertAlert(conn, topic, severity, "[" + ruleName + "] Missing required field: " + field);
                        }
                    } else if ("INVALID_TYPE".equals(type)) {
                        if (val != null && field.equals("amount") && !(val instanceof Number)) {
                            invalidCount++;
                            isRecordValid = false;
                            insertAlert(conn, topic, severity, "[" + ruleName + "] Field " + field + " is not a valid number");
                        }
                    } else if ("INVALID_EMAIL".equals(type)) {
                        if (val != null && !EMAIL_PATTERN.matcher(val.toString()).matches()) {
                            invalidCount++;
                            isRecordValid = false;
                            insertAlert(conn, topic, severity, "[" + ruleName + "] Invalid email format: " + val);
                        }
                    } else if ("INVALID_PHONE".equals(type)) {
                        if (val != null && !PHONE_PATTERN.matcher(val.toString()).matches()) {
                            invalidCount++;
                            isRecordValid = false;
                            insertAlert(conn, topic, severity, "[" + ruleName + "] Invalid phone length: " + val);
                        }
                    } else if ("CUSTOM_CHECK".equals(type)) {
                        if (val instanceof Number && threshold != null) {
                            double numVal = ((Number) val).doubleValue();
                            if (numVal < threshold) {
                                invalidCount++;
                                isRecordValid = false;
                                insertAlert(conn, topic, severity, "[" + ruleName + "] Threshold failure on " + field + " (" + numVal + " < " + threshold + ")");
                            }
                        }
                    } else if ("OUTLIER".equals(type)) {
                        if (val instanceof Number && threshold != null) {
                            double numVal = ((Number) val).doubleValue();
                            // Standalone simulator approximation for outlier without cluster history:
                            if (numVal > 10000.0) { // Large threshold trigger
                                outlierCount++;
                                insertAlert(conn, topic, severity, "[" + ruleName + "] High outlier detected: " + field + "=" + numVal);
                            }
                        }
                    }
                }

                // C. Check duplicates
                if (primaryKeyField != null && record.containsKey(primaryKeyField)) {
                    String id = String.valueOf(record.get(primaryKeyField));
                    if (seenIds.contains(id)) {
                        duplicateCount++;
                        isRecordValid = false;
                    } else {
                        seenIds.add(id);
                    }
                }

                if (isRecordValid) {
                    validRecords.add(record);
                } else {
                    invalidRecords.add(record);
                }
            }

            // 2. Compute aggregated metrics percentages
            double missingPct = ((double) missingCount / totalRecords) * 100.0;
            double duplicatePct = ((double) duplicateCount / totalRecords) * 100.0;
            double invalidPct = ((double) invalidCount / totalRecords) * 100.0;

            double schemaPenalty = schemaChanges > 0 ? 20.0 : 0.0;
            double outlierPenalty = outlierCount > 0 ? 15.0 : 0.0;
            double score = 100.0 - (missingPct * 0.3 + duplicatePct * 0.2 + schemaPenalty + invalidPct * 0.15 + outlierPenalty);
            score = Math.max(0.0, Math.min(100.0, score));

            // 3. Persist Metrics to quality_metrics via JDBC
            insertMetrics(conn, topic, missingPct, duplicatePct, invalidPct, schemaChanges, outlierCount, score);

            // 4. Save structured outputs (Simulated HDFS)
            saveRecordsToHdfs("storage/spark-processed/" + topic + ".json", validRecords);
            saveRecordsToHdfs("storage/spark-dead-letter/" + topic + ".json", invalidRecords);

        } catch (Exception e) {
            System.err.println("Spark failed to process micro-batch for topic " + topic + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { /* ignored */ }
            }
        }
    }

    private static List<Map<String, Object>> fetchRules(Connection conn, String sourceId) throws Exception {
        List<Map<String, Object>> rules = new ArrayList<>();
        String sql = "SELECT rule_name, rule_type, field_name, threshold, severity FROM validation_rules WHERE source_id = ? AND enabled = true";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sourceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> rule = new HashMap<>();
                    rule.put("rule_name", rs.getString("rule_name"));
                    rule.put("rule_type", rs.getString("rule_type"));
                    rule.put("field_name", rs.getString("field_name"));
                    rule.put("threshold", rs.getObject("threshold") != null ? rs.getDouble("threshold") : null);
                    rule.put("severity", rs.getString("severity"));
                    rules.add(rule);
                }
            }
        }
        return rules;
    }

    private static void insertMetrics(Connection conn, String sourceId, double missing, double dup, double invalid, int schema, int outlier, double score) throws Exception {
        String sql = "INSERT INTO quality_metrics (source_id, timestamp, missing_percentage, duplicate_percentage, invalid_percentage, schema_changes, outlier_count, quality_score) " +
                "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sourceId);
            stmt.setDouble(2, missing);
            stmt.setDouble(3, dup);
            stmt.setDouble(4, invalid);
            stmt.setInt(5, schema);
            stmt.setInt(6, outlier);
            stmt.setDouble(7, score);
            stmt.executeUpdate();
        }
    }

    private static void insertAlert(Connection conn, String sourceId, String severity, String message) throws Exception {
        String sql = "INSERT INTO alerts (source_id, severity, message, created_at, resolved) VALUES (?, ?, ?, CURRENT_TIMESTAMP, false)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sourceId);
            stmt.setString(2, severity);
            stmt.setString(3, message);
            stmt.executeUpdate();
        }
    }

    private static Set<String> getExpectedSchema(String topic) {
        return switch (topic) {
            case "sales-data" -> new HashSet<>(Arrays.asList("transactionId", "customerId", "productId", "amount", "timestamp"));
            case "customer-data" -> new HashSet<>(Arrays.asList("customerId", "name", "email", "phone", "timestamp"));
            case "sensor-data" -> new HashSet<>(Arrays.asList("sensorId", "temperature", "humidity", "timestamp"));
            case "payment-data" -> new HashSet<>(Arrays.asList("paymentId", "orderId", "amount", "paymentMethod", "timestamp"));
            case "inventory-data" -> new HashSet<>(Arrays.asList("productId", "quantity", "warehouseId", "timestamp"));
            default -> null;
        };
    }

    private static String getPrimaryKeyField(String topic) {
        return switch (topic) {
            case "sales-data" -> "transactionId";
            case "customer-data" -> "customerId";
            case "sensor-data" -> "sensorId";
            case "payment-data" -> "paymentId";
            case "inventory-data" -> "productId";
            default -> null;
        };
    }

    private static void saveRecordsToHdfs(String path, List<Map<String, Object>> records) {
        if (records.isEmpty()) return;
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter fw = new FileWriter(file, true)) {
                for (Map<String, Object> r : records) {
                    fw.write(objectMapper.writeValueAsString(r) + "\n");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to write to file " + path + ": " + e.getMessage());
        }
    }

    private static void saveRawRecordToFile(String path, String data) {
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write(data + "\n");
            }
        } catch (Exception e) {
            System.err.println("Failed to write to file " + path + ": " + e.getMessage());
        }
    }
}
