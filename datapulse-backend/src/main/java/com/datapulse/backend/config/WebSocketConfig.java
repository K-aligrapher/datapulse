package com.datapulse.backend.config;

import com.datapulse.backend.service.WebSocketService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketService webSocketService;

    public WebSocketConfig(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new MetricsWebSocketHandler(webSocketService), "/ws/metrics")
                .setAllowedOrigins("*");
    }

    private static class MetricsWebSocketHandler extends TextWebSocketHandler {
        private final WebSocketService webSocketService;

        public MetricsWebSocketHandler(WebSocketService webSocketService) {
            this.webSocketService = webSocketService;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            webSocketService.addSession(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            webSocketService.removeSession(session);
        }
    }
}
