package com.datapulse.backend.service;

import com.datapulse.backend.model.Alert;
import com.datapulse.backend.model.QualityMetric;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void addSession(WebSocketSession session) {
        sessions.add(session);
        logger.info("New WebSocket session registered: {}", session.getId());
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        logger.info("WebSocket session closed: {}", session.getId());
    }

    public void broadcastThroughput(String topic, long rate, int batchSize) {
        broadcast(Map.of(
            "type", "THROUGHPUT",
            "topic", topic,
            "rate", rate,
            "batchSize", batchSize,
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void broadcastMetric(QualityMetric metric) {
        broadcast(Map.of(
            "type", "METRIC",
            "sourceId", metric.getSourceId(),
            "missingPercentage", metric.getMissingPercentage(),
            "duplicatePercentage", metric.getDuplicatePercentage(),
            "invalidPercentage", metric.getInvalidPercentage(),
            "schemaChanges", metric.getSchemaChanges(),
            "outlierCount", metric.getOutlierCount(),
            "qualityScore", metric.getQualityScore(),
            "timestamp", metric.getTimestamp().toString()
        ));
    }

    public void broadcastAlert(Alert alert) {
        broadcast(Map.of(
            "type", "ALERT",
            "alertId", alert.getAlertId(),
            "sourceId", alert.getSourceId(),
            "severity", alert.getSeverity(),
            "message", alert.getMessage(),
            "createdAt", alert.getCreatedAt().toString(),
            "resolved", alert.getResolved()
        ));
    }

    private void broadcast(Object payload) {
        if (sessions.isEmpty()) return;
        
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        logger.warn("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to serialize broadcast payload", e);
        }
    }
}
