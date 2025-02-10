package com.dataflow.dataflowsystem.generator.handler;

import com.dataflow.dataflowsystem.generator.aop.MonitorMetrics;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    @Getter
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper;

    public WebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection established: {}", session.getId());
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    @MonitorMetrics(value = "websocket_send", operation = "send_batch")
    public void sendBatch(List<DataRecordMessage> records) throws JsonProcessingException {
        String message = objectMapper.writeValueAsString(records);

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                log.info("Removing closed session: {}", session.getId());
                sessions.remove(session);
                continue;
            }

            try {
                session.sendMessage(new TextMessage(message));
                log.info("Batch sent to session {}: {}", session.getId(), message);
            } catch (IOException e) {
                log.error("Error sending batch to session {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    @Scheduled(fixedRateString = "${websocket.cleanup-interval}")
    public void cleanupInactiveSessions() {
        sessions.removeIf(session -> {
            if (!session.isOpen()) {
                log.info("Cleaning up inactive session: {}", session.getId());
                return true;
            }
            return false;
        });
    }
}
