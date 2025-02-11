package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {
    private final DataProcessor dataProcessor;
    private final ObjectMapper objectMapper;

    public WebSocketHandler(DataProcessor dataProcessor) {
        this.dataProcessor = dataProcessor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @CircuitBreaker(name = "websocketHandlerCircuitBreaker", fallbackMethod = "fallbackHandleTextMessage")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            List<DataRecordMessage> dataRecords = objectMapper.readValue(
                    message.getPayload(), new TypeReference<>() {
                    });

            log.info("Received batch of {} messages", dataRecords.size());

            dataProcessor.processBatch(dataRecords);
        } catch (Exception e) {
            log.error("Error processing WebSocket batch message: {}", e.getMessage(), e);
        }
    }

    public void fallbackHandleTextMessage(WebSocketSession session, TextMessage message, Throwable t) {
        log.warn("WebSocket handler circuit breaker triggered: {}", t.getMessage());
    }
}
