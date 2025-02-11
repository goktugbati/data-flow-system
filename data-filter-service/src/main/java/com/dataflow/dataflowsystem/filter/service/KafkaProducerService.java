package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.aop.MonitorMetrics;
import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class KafkaProducerService {
    private final KafkaTemplate<String, DataRecordMessage> kafkaTemplate;

    @Value("${kafka.topic.name}")
    private String topic;

    public KafkaProducerService(KafkaTemplate<String, DataRecordMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @CircuitBreaker(name = "kafkaCircuitBreaker", fallbackMethod = "fallbackSendBatch")
    @Retry(name = "kafkaRetry", fallbackMethod = "fallbackRetrySendBatch")
    @MonitorMetrics(value = "kafka", operation = "send_batch")
    public void sendBatch(List<DataRecordMessage> messages) {
        messages.forEach(message -> kafkaTemplate.send(topic, message));
        log.info("Sent {} messages with Kafka’s internal batching.", messages.size());
    }

    public void fallbackSendBatch(List<DataRecordMessage> messages, Throwable t) {
        log.warn("Kafka batch send failed after retries. Error: {}", t.getMessage(), t);
    }

    public void fallbackRetrySendBatch(List<DataRecordMessage> messages, Throwable t) {
        log.warn("Kafka batch send failed after retries. Error: {}", t.getMessage(), t);
    }
}