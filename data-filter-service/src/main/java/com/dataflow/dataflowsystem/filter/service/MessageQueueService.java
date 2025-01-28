package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.config.RabbitMQProperties;
import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageQueueService {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;
    private final CircuitBreaker circuitBreaker;

    public MessageQueueService(
            RabbitTemplate rabbitTemplate,
            RabbitMQProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("messageQueueCircuitBreaker");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Message queue circuit breaker state changed from {} to {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("Message queue circuit breaker recorded error: {}",
                                event.getThrowable().getMessage()));
    }

    public void send(DataRecordMessage record) {
        Runnable decoratedSend = CircuitBreaker
                .decorateRunnable(circuitBreaker, () -> {
                    try {
                        rabbitTemplate.convertAndSend(
                                properties.getExchangeName(),
                                properties.getRoutingKey(),
                                record
                        );
                        log.debug("Successfully sent message to queue: {}", record);
                    } catch (Exception e) {
                        log.error("Failed to send message to queue: {}", e.getMessage());
                        throw new RuntimeException("Message queue send failed", e);
                    }
                });

        try {
            decoratedSend.run();
        } catch (Exception e) {
            log.error("Circuit breaker prevented message queue send: {}", e.getMessage());
            sendToDlq(record);
        }
    }

    private void sendToDlq(DataRecordMessage record) {
        try {
            rabbitTemplate.convertAndSend(
                    properties.getExchangeName(),
                    properties.getRoutingKey() + ".dlq",
                    record
            );
            log.info("Sent message to DLQ: {}", record);
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage());
        }
    }

    public void checkConnection() {
        try {
            rabbitTemplate.execute(channel -> {
                channel.getConnection().isOpen();
                return null;
            });
        } catch (Exception e) {
            log.error("RabbitMQ connection check failed: {}", e.getMessage());
            throw new RuntimeException("RabbitMQ connection check failed", e);
        }
    }
}
