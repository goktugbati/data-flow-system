package com.dataflow.dataflowsystem.filter.config;

import com.dataflow.dataflowsystem.filter.service.DataProcessor;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {
    private final CircuitBreaker circuitBreaker;
    private final DataProcessor dataProcessor;
    private final ObjectMapper objectMapper;

    public WebSocketHandler(CircuitBreaker circuitBreaker, DataProcessor dataProcessor) {
        this.circuitBreaker = circuitBreaker;
        this.dataProcessor = dataProcessor;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Runnable decoratedHandler = CircuitBreaker
                    .decorateRunnable(circuitBreaker, () -> {
                        try {
                            DataRecordMessage record = objectMapper.readValue(
                                    message.getPayload(),
                                    DataRecordMessage.class
                            );
                            dataProcessor.processData(record);
                        } catch (Exception e) {
                            log.error("Error processing message: {}", e.getMessage());
                            throw new RuntimeException("Message processing failed", e);
                        }
                    });

            decoratedHandler.run();
        } catch (Exception e) {
            log.error("Circuit breaker prevented message processing: {}", e.getMessage());
        }
    }
}