package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.config.WebSocketProperties;
import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import io.micrometer.core.instrument.Timer;

import java.util.Random;

@Service
@Slf4j
public class DataGeneratorService {

    private final WebSocketHandler webSocketHandler;
    private final WebSocketProperties webSocketProperties;
    private final Random random = new Random();
    private final MeterRegistry meterRegistry;
    private final Timer generationTimer;

    public DataGeneratorService(WebSocketHandler webSocketHandler,
                                WebSocketProperties webSocketProperties,
                                MeterRegistry meterRegistry) {
        this.webSocketHandler = webSocketHandler;
        this.webSocketProperties = webSocketProperties;
        this.meterRegistry = meterRegistry;
        this.generationTimer = meterRegistry.timer("data.generator.time");
    }

    @Scheduled(fixedRate = 200)
    public void generateAndSendData() {
        generationTimer.record(() -> {
            DataRecordMessage record = generateData();
            boolean success = false;
            int attempts = 0;

            while (!success && attempts < webSocketProperties.getMaxRetryAttempts()) {
                try {
                    webSocketHandler.sendMessage(record);
                    meterRegistry.counter("data.generator.success").increment();
                    log.info("Generated and sent data: {}", record);
                    success = true;
                } catch (Exception e) {
                    attempts++;
                    meterRegistry.counter("data.generator.retries").increment();
                    log.warn("Error sending data (Attempt {}/{}): {}", attempts, webSocketProperties.getMaxRetryAttempts(), e.getMessage());
                    if (attempts == webSocketProperties.getMaxRetryAttempts()) {
                        meterRegistry.counter("data.generator.failures").increment();
                        log.error("Failed to send data after {} attempts: {}", webSocketProperties.getMaxRetryAttempts(), record);
                    }
                }
            }
        });
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

