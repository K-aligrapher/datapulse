package com.datapulse.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DataSimulatorService {

    private static final Logger logger = LoggerFactory.getLogger(DataSimulatorService.class);
    private final IngestionService ingestionService;
    private ScheduledExecutorService executorService;
    private boolean running = false;
    private boolean induceAnomalies = false;
    private final Random random = new Random();

    // Cache to generate duplicates
    private String lastSalesId = null;
    private String lastCustomerId = null;

    private final String[] NAMES = {"John Doe", "Jane Smith", "Alice Johnson", "Bob Brown", "Charlie Green", "David Black"};
    private final String[] PAYMENTS = {"CREDIT_CARD", "PAYPAL", "APPLE_PAY", "BANK_TRANSFER"};

    public DataSimulatorService(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executorService = Executors.newSingleThreadScheduledExecutor();
        // Generate an event every 300ms (approx 3.3 events per second)
        executorService.scheduleAtFixedRate(this::generateEvent, 0, 300, TimeUnit.MILLISECONDS);
        logger.info("DataPulse Real-Time Event Simulator started");
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (executorService != null) {
            executorService.shutdown();
        }
        logger.info("DataPulse Real-Time Event Simulator stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public void setInduceAnomalies(boolean induce) {
        this.induceAnomalies = induce;
    }

    public boolean isInduceAnomalies() {
        return induceAnomalies;
    }

    private void generateEvent() {
        try {
            int type = random.nextInt(5);
            String topic;
            String payload;

            switch (type) {
                case 0 -> {
                    topic = "sales-data";
                    payload = generateSalesEvent();
                }
                case 1 -> {
                    topic = "customer-data";
                    payload = generateCustomerEvent();
                }
                case 2 -> {
                    topic = "sensor-data";
                    payload = generateSensorEvent();
                }
                case 3 -> {
                    topic = "payment-data";
                    payload = generatePaymentEvent();
                }
                default -> {
                    topic = "inventory-data";
                    payload = generateInventoryEvent();
                }
            }

            ingestionService.ingestEvent(topic, payload);
        } catch (Exception e) {
            logger.error("Simulator failed to generate event", e);
        }
    }

    private String generateSalesEvent() {
        String txId = "TX-" + (1000 + random.nextInt(9000));
        String custId = "C-" + (100 + random.nextInt(900));
        String prodId = "P-" + (10 + random.nextInt(90));
        double amount = 10.0 + (random.nextDouble() * 240.0); // 10.0 to 250.0

        if (induceAnomalies) {
            if (random.nextDouble() < 0.15 && lastSalesId != null) {
                txId = lastSalesId; // Duplicate ID
            }
            if (random.nextDouble() < 0.20) {
                custId = ""; // Missing Required Field
            }
            if (random.nextDouble() < 0.15) {
                amount = 150000.0 + random.nextDouble() * 50000.0; // Statistical Outlier
            }
            if (random.nextDouble() < 0.10) {
                amount = -50.0; // Threshold failure (Amount > 0 check)
            }
        }

        lastSalesId = txId;
        return String.format(
            "{\"transactionId\":\"%s\",\"customerId\":\"%s\",\"productId\":\"%s\",\"amount\":%.2f,\"timestamp\":\"%s\"}",
            txId, custId, prodId, amount, DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        );
    }

    private String generateCustomerEvent() {
        String custId = "C-" + (100 + random.nextInt(900));
        String name = NAMES[random.nextInt(NAMES.length)];
        String email = name.toLowerCase().replace(" ", ".") + "@example.com";
        String phone = String.format("%010d", random.nextLong(1000000000L, 9999999999L));

        if (induceAnomalies) {
            if (random.nextDouble() < 0.15 && lastCustomerId != null) {
                custId = lastCustomerId; // Duplicate customer ID
            }
            if (random.nextDouble() < 0.20) {
                email = name.toLowerCase().replace(" ", ".") + "example.com"; // Invalid Email
            }
            if (random.nextDouble() < 0.20) {
                phone = "12345"; // Invalid Phone Number (Length != 10)
            }
        }

        lastCustomerId = custId;
        return String.format(
            "{\"customerId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"timestamp\":\"%s\"}",
            custId, name, email, phone, DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        );
    }

    private String generateSensorEvent() {
        String sensorId = "S-" + (100 + random.nextInt(900));
        double temp = 20.0 + (random.nextDouble() * 15.0); // 20.0 to 35.0 Celsius
        double humidity = 35.0 + (random.nextDouble() * 30.0); // 35.0 to 65.0 %

        if (induceAnomalies) {
            if (random.nextDouble() < 0.20) {
                temp = 999.9; // Threshold check (>100 limit check) and outlier
            }
        }

        return String.format(
            "{\"sensorId\":\"%s\",\"temperature\":%.2f,\"humidity\":%.2f,\"timestamp\":\"%s\"}",
            sensorId, temp, humidity, DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        );
    }

    private String generatePaymentEvent() {
        String payId = "PAY-" + (1000 + random.nextInt(9000));
        String orderId = "TX-" + (1000 + random.nextInt(9000));
        double amount = 10.0 + (random.nextDouble() * 240.0);
        String method = PAYMENTS[random.nextInt(PAYMENTS.length)];

        if (induceAnomalies) {
            if (random.nextDouble() < 0.20) {
                amount = -1.0; // Invalid Payment Amount
            }
        }

        return String.format(
            "{\"paymentId\":\"%s\",\"orderId\":\"%s\",\"amount\":%.2f,\"paymentMethod\":\"%s\",\"timestamp\":\"%s\"}",
            payId, orderId, amount, method, DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        );
    }

    private String generateInventoryEvent() {
        String prodId = "P-" + (10 + random.nextInt(90));
        int quantity = 10 + random.nextInt(490);
        String warehouseId = "W-" + (1 + random.nextInt(5));

        if (induceAnomalies) {
            if (random.nextDouble() < 0.20) {
                quantity = -5; // Negative quantity check
            }
        }

        return String.format(
            "{\"productId\":\"%s\",\"quantity\":%d,\"warehouseId\":\"%s\",\"timestamp\":\"%s\"}",
            prodId, quantity, warehouseId, DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        );
    }
}
