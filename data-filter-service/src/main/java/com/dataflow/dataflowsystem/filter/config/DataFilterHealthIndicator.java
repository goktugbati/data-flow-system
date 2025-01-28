package com.dataflow.dataflowsystem.filter.config;

import com.dataflow.dataflowsystem.filter.service.MessageQueueService;
import com.dataflow.dataflowsystem.filter.service.WebSocketClientService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class DataFilterHealthIndicator implements HealthIndicator {
    private final WebSocketClientService webSocketClient;
    private final MessageQueueService messageQueue;

    public DataFilterHealthIndicator(WebSocketClientService webSocketClient, MessageQueueService messageQueue) {
        this.webSocketClient = webSocketClient;
        this.messageQueue = messageQueue;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        if (webSocketClient.isConnected()) {
            builder.up();
        } else {
            builder.down().withDetail("websocket", "Disconnected");
        }

        try {
            messageQueue.checkConnection();
            builder.withDetail("rabbitmq", "Connected");
        } catch (Exception e) {
            builder.down().withDetail("rabbitmq", "Connection failed");
        }

        return builder.build();
    }

}
