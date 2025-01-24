package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Random;

@Service
@Slf4j
public class DataGeneratorService {
    private final WebSocketHandler webSocketHandler;
    private final Random random = new Random();

    public DataGeneratorService(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 200)
    public void generateAndSendData() {
        try {
            DataRecord record = generateData();
            webSocketHandler.sendMessage(record);
            log.info("Generated and sent data: {}", record);
        } catch (Exception e) {
            log.error("Error generating/sending data: {}", e.getMessage());
        }
    }

    public DataRecord generateData() {
        Long timestamp = System.currentTimeMillis();
        Integer randomValue = random.nextInt(101);
        String hashValue = generateHashValue(timestamp, randomValue);
        return new DataRecord(timestamp, randomValue, hashValue);
    }

    public String generateHashValue(Long timestamp, Integer value) {
        String combined = timestamp.toString() + value.toString();
        String combinedMd5 = DigestUtils.md5DigestAsHex(combined.getBytes());
        return combinedMd5.substring(combinedMd5.length() - 2);
    }
}
