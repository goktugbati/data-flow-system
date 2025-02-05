package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.aop.MonitorMetrics;
import com.dataflow.dataflowsystem.generator.config.WebSocketProperties;
import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Random;

@Service
@Slf4j
public class DataGeneratorService {

    private final WebSocketHandler webSocketHandler;
    private final WebSocketProperties webSocketProperties;
    private final FilterServiceHealthCheckService filterServiceHealthCheckService;
    private final Random random = new Random();

    public DataGeneratorService(WebSocketHandler webSocketHandler,
                                WebSocketProperties webSocketProperties,
                                FilterServiceHealthCheckService filterServiceHealthCheckService) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketProperties = webSocketProperties;
        this.filterServiceHealthCheckService = filterServiceHealthCheckService;
    }

    @Scheduled(fixedRate = 200)
    @MonitorMetrics(value = "websocket_send", operation = "send_message")
    public void generateAndSendData() {
        if (!filterServiceHealthCheckService.isFilterServiceAvailable()) {
            log.warn("Filter service is down. Skipping data generation.");
            return;
        }

        DataRecordMessage record = generateData();
        boolean success = false;
        int attempts = 0;

        while (!success && attempts < webSocketProperties.getMaxRetryAttempts()) {
            try {
                webSocketHandler.sendMessage(record);
                log.info("Generated and sent data: {}", record);
                success = true;
            } catch (Exception e) {
                attempts++;
                log.warn("Error sending data (Attempt {}/{}): {}", attempts, webSocketProperties.getMaxRetryAttempts(), e.getMessage());
                if (attempts == webSocketProperties.getMaxRetryAttempts()) {
                    log.error("Failed to send data after {} attempts: {}", webSocketProperties.getMaxRetryAttempts(), record);
                }
            }
        }
    }

    public DataRecordMessage generateData() {
        Long timestamp = System.currentTimeMillis();
        Integer randomValue = random.nextInt(101);
        String hashValue = generateHashValue(timestamp, randomValue);
        return new DataRecordMessage(timestamp, randomValue, hashValue);
    }

    public String generateHashValue(Long timestamp, Integer value) {
        String combined = timestamp + value.toString();
        String combinedMd5 = DigestUtils.md5DigestAsHex(combined.getBytes());
        return combinedMd5.substring(combinedMd5.length() - 2);
    }
}
