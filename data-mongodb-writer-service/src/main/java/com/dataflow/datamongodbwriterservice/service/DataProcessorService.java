package com.dataflow.datamongodbwriterservice.service;

import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DataProcessorService {
    private final MongoDbService mongoDbService;

    public DataProcessorService(MongoDbService mongoDbService) {
        this.mongoDbService = mongoDbService;
    }

    @KafkaListener(
            topics = "${kafka.topic.name}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "mongoDbCircuitBreaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "mongoDbRetry", fallbackMethod = "handleRetryFallback")
    public void processMessages(
            @Payload List<DataRecordMessage> messages,
            @Header(KafkaHeaders.ACKNOWLEDGMENT) Acknowledgment acknowledgment) {
        try {
            log.debug("Received batch of {} messages", messages.size());
            mongoDbService.processBatch(messages);
            acknowledgment.acknowledge();
            log.debug("Successfully processed and acknowledged batch of {} messages", messages.size());
        } catch (Exception e) {
            log.error("Error processing message batch: {}", e.getMessage());
            throw e;
        }
    }

    private void handleCircuitBreakerFallback(List<DataRecordMessage> messages, Acknowledgment acknowledgment, Throwable t) {
        log.error("Circuit breaker triggered for batch processing: {}", t.getMessage());
        throw new RuntimeException("Circuit breaker opened", t);
    }

    private void handleRetryFallback(List<DataRecordMessage> messages, Acknowledgment acknowledgment, Throwable t) {
        log.error("All retries exhausted for batch processing: {}", t.getMessage());
        // Consider DLQ or other error handling strategies
        acknowledgment.acknowledge(); // Prevent infinite retries
    }
}