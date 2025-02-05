package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.aop.MonitorMetrics;
import com.dataflow.dataflowsystem.filter.config.RabbitMQProperties;
import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageQueueService {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;

    public MessageQueueService(RabbitTemplate rabbitTemplate, RabbitMQProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @MonitorMetrics(value = "queue", operation = "send_message")
    @CircuitBreaker(name = "messageQueueCircuitBreaker", fallbackMethod = "sendToDlq")
    public void send(DataRecordMessage record) {
        rabbitTemplate.convertAndSend(properties.getExchangeName(), properties.getRoutingKey(), record);
        log.debug("Successfully sent message to queue: {}", record);
    }

    public void sendToDlq(DataRecordMessage record, Throwable throwable) {
        log.warn("Circuit breaker is OPEN. Redirecting to DLQ due to: {}", throwable.getMessage());
        rabbitTemplate.convertAndSend(properties.getExchangeName(), properties.getRoutingKey() + ".dlq", record);
        log.info("Sent message to DLQ: {}", record);
    }

    public void checkConnection() {
        try {
            rabbitTemplate.execute(channel -> {
                if (channel.getConnection().isOpen()) {
                    log.debug("RabbitMQ connection is healthy");
                }
                return null;
            });
        } catch (Exception e) {
            log.error("RabbitMQ connection check failed: {}", e.getMessage());
            throw new RuntimeException("RabbitMQ connection check failed", e);
        }
    }
}

