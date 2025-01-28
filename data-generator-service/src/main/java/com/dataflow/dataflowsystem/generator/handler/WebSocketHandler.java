package com.dataflow.dataflowsystem.generator.handler;

import com.dataflow.dataflowsystem.generator.config.WebSocketProperties;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry meterRegistry;

    public WebSocketHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Set<WebSocketSession> getSessions() {
        return sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection established: {}", session.getId());
        sessions.add(session);
        meterRegistry.counter("websocket.connections.opened").increment();
        meterRegistry.gauge("websocket.connections.active", sessions, Set::size);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        sessions.remove(session);
        meterRegistry.counter("websocket.connections.closed").increment();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
        meterRegistry.counter("websocket.errors").increment();
    }

    public void sendMessage(DataRecordMessage record) throws JsonProcessingException {
        String message = objectMapper.writeValueAsString(record);

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                log.info("Removing closed session: {}", session.getId());
                sessions.remove(session);
                continue;
            }

            try {
                session.sendMessage(new TextMessage(message));
                log.info("Message sent to session {}: {}", session.getId(), message);
            } catch (IOException e) {
                log.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
                meterRegistry.counter("websocket.send.failures").increment();
            }
        }
    }

    @Scheduled(fixedRateString = "#{webSocketProperties.cleanupIntervalMs}")
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
