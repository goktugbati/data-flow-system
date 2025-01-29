package com.dataflow.datamongodbwriterservice.service;


import com.dataflow.datamongodbwriterservice.config.RabbitMQProperties;
import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class DataProcessorService {
    private final DataRecordMongoRepository mongoRepo;
    private final CircuitBreaker circuitBreaker;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;

    public DataProcessorService(
            DataRecordMongoRepository mongoRepo,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RabbitTemplate rabbitTemplate,
            RabbitMQProperties rabbitMQProperties) {
        this.mongoRepo = mongoRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQProperties = rabbitMQProperties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("mongoWriterCircuitBreaker");
        setupCircuitBreakerEvents();
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}", ackMode = "MANUAL", concurrency = "3-10")
    public void processMessage(DataRecordMessage record, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                               @Header(value = "x-retry-count", defaultValue = "0") int retryCount) {
        log.debug("Received message: {}, retry count: {}", record, retryCount);
        try {
            Runnable decoratedProcess = CircuitBreaker
                    .decorateRunnable(circuitBreaker, () -> {
                        processAndSaveRecord(record);
                    });

            decoratedProcess.run();
            channel.basicAck(deliveryTag, false);
            log.debug("Message acknowledged: {}", deliveryTag);

        } catch (Exception e) {
            log.error("Error processing message: {}", record, e);
            handleProcessingFailure(record, channel, deliveryTag, retryCount, e);
        }
    }

    private void processAndSaveRecord(DataRecordMessage record) {
        String messageId = generateMessageId(record);
        if (mongoRepo.existsById(messageId)) {
            log.info("Message already processed: {}", record);
            return;
        }

        Optional<DataRecordDocument> lastRecord = mongoRepo.findFirstByOrderByTimestampDesc();
        if (lastRecord.isPresent() && shouldNest(record)) {
            updateExistingRecord(lastRecord.get(), record, messageId);
        } else {
            createNewRecord(record, messageId);
        }
    }

    private void updateExistingRecord(DataRecordDocument lastDoc, DataRecordMessage record, String messageId) {
        DataRecordDocument nestedRecord = DataRecordDocument.fromDataRecord(record, messageId);
        try {
            mongoRepo.addNestedRecord(lastDoc.getId(), nestedRecord);
            log.info("Updated record with nested data for id: {}", lastDoc.getId());
        } catch (Exception e) {
            log.error("Error updating document with id {}: {}", lastDoc.getId(), e.getMessage());
            throw e;
        }
    }

    private boolean shouldNest(DataRecordMessage record) {
        try {
            int hashValue = Integer.parseInt(record.getHashValue(), 16); // Hex to decimal
            return hashValue > 0x99;
        } catch (NumberFormatException e) {
            log.error("Invalid hash value: {}", record.getHashValue());
            return false;
        }
    }

    private void createNewRecord(DataRecordMessage record, String messageId) {
        DataRecordDocument newDoc = DataRecordDocument.fromDataRecord(record, messageId);
        try {
            mongoRepo.insert(newDoc);
            log.info("Created new record with id: {}", messageId);
        } catch (Exception e) {
            log.error("Error creating new document with id {}: {}", messageId, e.getMessage());
            throw e;
        }
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

    private void setupCircuitBreakerEvents() {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Circuit breaker state changed from {} to {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("Circuit breaker recorded error: {}",
                                event.getThrowable().getMessage()));
    }

    private String generateMessageId(DataRecordMessage record) {
        return record.getHashValue() + ":" + record.getTimestamp();
    }
}