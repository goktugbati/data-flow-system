package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Service
@Slf4j
public class WebSocketClientService {
    private WebSocketSession session;
    private final DataProcessor dataProcessor;
    @Value("${websocket.generator.url}")
    private String serverUri;


    public WebSocketClientService(DataProcessor dataProcessor) {
        this.dataProcessor = dataProcessor;
    }

    @PostConstruct
    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();
        int retryCount = 0;
        int maxRetries = 5;

        while (retryCount < maxRetries) {
            try {
                log.info("Attempting to connect to WebSocket at: {} (Attempt {}/{})", serverUri, retryCount + 1, maxRetries);
                session = client.execute(new WebSocketHandler(), serverUri).get();
                log.info("Successfully connected to WebSocket at: {}", serverUri);
                break;
            } catch (Exception e) {
                retryCount++;
                log.error("Failed to connect to WebSocket at {} (Attempt {}/{}): ", serverUri, retryCount, maxRetries, e);
                try {
                    Thread.sleep(2000); // Wait for 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry interrupted: ", ie);
                    break;
                }
            }
        }

        if (retryCount == maxRetries) {
            log.error("Max retry attempts reached. Could not connect to WebSocket at: {}", serverUri);
        }
    }

    @PreDestroy
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                log.info("WebSocket session closed.");
            } catch (Exception e) {
                log.error("Error closing WebSocket session: {}", e.getMessage());
            }
        }
    }

    private class WebSocketHandler extends TextWebSocketHandler {
        @Override
        public void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                log.info("Received message: {}", message.getPayload());
                DataRecordMessage record = mapper.readValue(message.getPayload(), DataRecordMessage.class);
                log.info("Received message: {}", record);
                dataProcessor.processData(record);
            } catch (Exception e) {
                log.error("Error processing message: {}", e.getMessage());
            }
        }
    }
}