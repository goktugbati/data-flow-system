package com.dataflow.dataflowsystem.filter.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Service
@Slf4j
public class WebSocketClientService {
    private WebSocketSession session;
    private final DataProcessor dataProcessor;

    @Value("${websocket.generator.url}")
    private String serverUri;
    private volatile boolean connected = false;

    public WebSocketClientService(DataProcessor dataProcessor) {
        this.dataProcessor = dataProcessor;
    }

    @PreDestroy
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                connected = false;
                log.info("WebSocket session closed.");
            } catch (Exception e) {
                log.error("Error closing WebSocket session: {}", e.getMessage());
            }
        }
    }

    @PostConstruct
    @CircuitBreaker(name = "webSocketCircuitBreaker", fallbackMethod = "fallbackConnect")
    @Retry(name = "websocketRetry", fallbackMethod = "fallbackConnect")
    public void connect() {
        try {
            WebSocketClient client = new StandardWebSocketClient();
            session = client.execute(
                    new WebSocketHandler(dataProcessor),
                    serverUri
            ).get();
            connected = true;
            log.info("Successfully connected to WebSocket at: {}", serverUri);
        } catch (Exception e) {
            connected = false;
            log.error("Failed to connect to WebSocket: {}", e.getMessage());
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    public void fallbackConnect(Throwable t) {
        log.warn("WebSocket connection failed after retries: {}", t.getMessage(), t);
        connected = false;
    }

    public boolean isConnected() {
        return session != null && session.isOpen() && connected;
    }

    @CircuitBreaker(name = "webSocketCircuitBreaker", fallbackMethod = "reconnectFallback")
    public void reconnect() {
        log.info("Attempting to reconnect to WebSocket");
        disconnect();
        connect();
    }

    private void reconnectFallback(Exception e) {
        log.error("Circuit breaker triggered for WebSocket reconnection: {}", e.getMessage());
        connected = false;
    }
}