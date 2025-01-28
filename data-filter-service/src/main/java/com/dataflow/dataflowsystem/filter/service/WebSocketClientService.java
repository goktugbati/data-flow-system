package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.config.WebSocketHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
    private final CircuitBreaker circuitBreaker;
    @Value("${websocket.generator.url}")
    private String serverUri;
    private volatile boolean connected = false;

    public WebSocketClientService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            DataProcessor dataProcessor) {
        this.dataProcessor = dataProcessor;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("webSocketCircuitBreaker");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Circuit breaker state changed from {} to {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("Circuit breaker recorded error: {}",
                                event.getThrowable().getMessage()));
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
    public void connect() {
        int retryCount = 0;
        int maxRetries = 5;

        while (retryCount < maxRetries) {
            try {
                Runnable decoratedConnect = CircuitBreaker
                        .decorateRunnable(circuitBreaker, () -> {
                            try {
                                WebSocketClient client = new StandardWebSocketClient();
                                session = client.execute(
                                        new WebSocketHandler(circuitBreaker, dataProcessor),
                                        serverUri
                                ).get();
                                connected = true;
                                log.info("Successfully connected to WebSocket at: {}", serverUri);
                            } catch (Exception e) {
                                connected = false;
                                log.error("Failed to connect to WebSocket: {}", e.getMessage());
                                throw new RuntimeException("WebSocket connection failed", e);
                            }
                        });

                decoratedConnect.run();
                break;
            } catch (Exception e) {
                connected = false;
                retryCount++;
                log.error("Failed to connect (Attempt {}/{}): {}",
                        retryCount, maxRetries, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public boolean isConnected() {
        return session != null && session.isOpen() && connected;
    }

    public void reconnect() {
        log.info("Attempting to reconnect to WebSocket");
        disconnect();
        connect();
    }

}