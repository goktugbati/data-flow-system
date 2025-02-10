package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.aop.MonitorMetrics;
import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class DataGeneratorService {

    private final WebSocketHandler webSocketHandler;
    private final RedisService redisService;
    private final Random random = new Random();

    @Value("${batch.size:100}")
    private int batchSize;

    @Value("${batch.processing-interval-ms:1000}")
    private long processingInterval;

    public DataGeneratorService(WebSocketHandler webSocketHandler,
                                RedisService redisService) {
        this.webSocketHandler = webSocketHandler;
        this.redisService = redisService;
    }

    @Scheduled(fixedRate = 200)
    @MonitorMetrics(value = "websocket_send", operation = "generate_message")
    @CircuitBreaker(name = "filterServiceCircuitBreaker", fallbackMethod = "fallbackGenerateData")
    public void generateData() {
        DataRecordMessage record = generateDataRecord();
        redisService.addToBatch("dataBatch", record);
        log.info("Generated data record: timestamp={}, randomValue={}, hashValue={}",
                record.getTimestamp(), record.getRandomValue(), record.getHashValue());
    }

    @Scheduled(fixedRateString = "${batch.processing-interval-ms:1000}")
    @MonitorMetrics(value = "websocket_send", operation = "send_batch")
    @CircuitBreaker(name = "websocketCircuitBreaker", fallbackMethod = "fallbackSendBatch")
    @Retry(name = "websocketRetry", fallbackMethod = "fallbackRetrySendBatch")
    public void sendBatch() {
        List<DataRecordMessage> batchCache = redisService.getBatch("dataBatch");

        if (batchCache.isEmpty()) {
            log.info("No data to send: Batch is empty.");
            return;
        }

        List<DataRecordMessage> limitedBatch = batchCache.stream()
                .limit(batchSize)
                .toList();

        try {
            webSocketHandler.sendBatch(limitedBatch);
            redisService.clearBatch("dataBatch");
            log.info("Successfully sent batch of size: {}", batchCache.size());
        } catch (Exception e) {
            log.error("Failed to send batch of size {}. Error: {}", batchCache.size(), e.getMessage(), e);
        }
    }

    public void fallbackSendBatch(Throwable t) {
        log.warn("Fallback triggered for sendBatch due to: {}", t.getMessage(), t);
    }

    public void fallbackGenerateData(Throwable t) {
        log.warn("Circuit breaker triggered for data generation: {}", t.getMessage(), t);
    }

    public void fallbackRetrySendBatch(Throwable t) {
        log.warn("Retry exhausted for batch sending: {}", t.getMessage(), t);
    }

    public DataRecordMessage generateDataRecord() {
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
