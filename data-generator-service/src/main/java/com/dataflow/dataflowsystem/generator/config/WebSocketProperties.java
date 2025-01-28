package com.dataflow.dataflowsystem.generator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {
    private int maxRetryAttempts;
    private long cleanupIntervalMs;
}
