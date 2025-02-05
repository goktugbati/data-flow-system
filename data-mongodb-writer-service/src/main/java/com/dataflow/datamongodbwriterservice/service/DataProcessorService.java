package com.dataflow.datamongodbwriterservice.service;


import com.dataflow.datamongodbwriterservice.config.RabbitMQProperties;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DataProcessorService {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;
    private final ObjectMapper objectMapper;
    private final MongoDbService mongoDbService;

    public DataProcessorService(
            DataRecordMongoRepository mongoRepo,
            RabbitTemplate rabbitTemplate,
            RabbitMQProperties rabbitMQProperties, ObjectMapper objectMapper, MongoDbService mongoDbService) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQProperties = rabbitMQProperties;
        this.objectMapper = objectMapper;
        this.mongoDbService = mongoDbService;
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}", ackMode = "MANUAL", concurrency = "3-10")
    @CircuitBreaker(name = "mongoDbCircuitBreaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "mongoDbRetry", fallbackMethod = "handleCircuitBreakerFallback")
    public void processMessage(Message message, Channel channel) throws IOException {
        try {
            DataRecordMessage record = deserializeMessage(message);
            log.debug("Received message: {}", record);
            mongoDbService.processAndSaveRecord(record);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("Error processing message", e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    private void handleCircuitBreakerFallback(Message message, Channel channel, Throwable t) {
        log.error("Circuit breaker triggered for message processing: {}", t.getMessage());
    }

    private DataRecordMessage deserializeMessage(Message message) throws IOException {
        return objectMapper.readValue(message.getBody(), DataRecordMessage.class);
    }

    private void handleProcessingFailure(DataRecordMessage record, Channel channel,
                                         long deliveryTag, int retryCount, Exception e) {
        try {
            if (retryCount < rabbitMQProperties.getMaxRetries()) {
                channel.basicNack(deliveryTag, false, true);
                log.debug("Message nacked and requeued: {}, retry: {}", deliveryTag, retryCount + 1);
            } else {
                sendToDlq(record, e);
                channel.basicAck(deliveryTag, false);
                log.info("Message sent to DLQ after {} retries: {}", rabbitMQProperties.getMaxRetries(), record);
            }
        } catch (IOException ioException) {
            log.error("Error handling message failure: {}", ioException.getMessage(), ioException);
        }
    }

    private void sendToDlq(DataRecordMessage record, Exception originalError) {
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("error_message", originalError.getMessage());
            headers.put("error_timestamp", System.currentTimeMillis());
            headers.put("original_routing_key", rabbitMQProperties.getRoutingKey());

            rabbitTemplate.convertAndSend(
                    rabbitMQProperties.getExchangeName(),
                    rabbitMQProperties.getRoutingKey() + ".dlq",
                    record,
                    message -> {
                        message.getMessageProperties().setHeaders(headers);
                        return message;
                    }
            );
            log.info("Sent message to DLQ: {}", record);
        } catch (Exception e) {
            log.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    private String generateMessageId(DataRecordMessage record) {
        return record.getHashValue() + ":" + record.getTimestamp();
    }
}