package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.datadbwriterservice.repository.DataRecordRepository;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataProcessorService {
    private static final String STREAM_KEY = "data:stream";
    private static final String CONSUMER_GROUP = "db-writers";

    private final DataRecordRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String consumerId;
    private final CircuitBreaker redisCircuitBreaker;
    private final CircuitBreaker dbCircuitBreaker;
    private final Counter processedMessagesCounter;
    private final Counter failedMessagesCounter;
    private final Timer batchProcessingTimer;

    public DataProcessorService(DataRecordRepository repository,
                                RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                MeterRegistry meterRegistry) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisCircuitBreaker = circuitBreakerRegistry.circuitBreaker("redisCircuitBreaker");
        this.dbCircuitBreaker = circuitBreakerRegistry.circuitBreaker("dbCircuitBreaker");
        this.consumerId = UUID.randomUUID().toString();
        this.processedMessagesCounter = Counter.builder("data.db.messages")
                .tag("status", "processed")
                .description("Number of messages processed successfully")
                .register(meterRegistry);
        this.failedMessagesCounter = Counter.builder("data.db.messages")
                .tag("status", "failed")
                .description("Number of failed message operations")
                .register(meterRegistry);
        this.batchProcessingTimer = Timer.builder("data.db.batch.processing")
                .description("Batch processing time")
                .register(meterRegistry);
        setupCircuitBreakerEvents();
    }

    @PostConstruct
    public void init() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("Created consumer group: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            log.warn("Consumer group initialization error (safe to ignore if already exists): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            redisTemplate.opsForStream().destroyGroup(STREAM_KEY, CONSUMER_GROUP);
            log.info("Cleaned up consumer group: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            log.warn("Error cleaning up consumer group: {}", e.getMessage());
        }
    }

    private void setupCircuitBreakerEvents() {
        redisCircuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Redis circuit breaker state changed from {} to {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("Redis circuit breaker recorded error: {}",
                                event.getThrowable().getMessage()));

        dbCircuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("DB circuit breaker state changed from {} to {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onError(event ->
                        log.error("DB circuit breaker recorded error: {}",
                                event.getThrowable().getMessage()));
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}")
    public void processMessage(DataRecordMessage message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            Runnable decoratedProcess = CircuitBreaker
                    .decorateRunnable(redisCircuitBreaker, () -> {
                        DataRecordEntity record = convertToEntity(message);
                        redisTemplate.opsForStream().add(
                                Record.of(objectMapper.convertValue(record, Map.class))
                                        .withStreamKey(STREAM_KEY)
                        );
                        log.debug("Added to stream: {}", record);
                    });

            decoratedProcess.run();
            if (channel.isOpen()) {
                channel.basicAck(deliveryTag, false);
                processedMessagesCounter.increment();
            } else {
                log.warn("Channel is closed, cannot acknowledge message with delivery tag: {}", deliveryTag);
            }
        } catch (Exception e) {
            log.error("Stream write error: {}", e.getMessage(), e);
            failedMessagesCounter.increment();
            handleProcessingFailure(message, channel, deliveryTag, e);
        }
    }

    @Scheduled(fixedDelayString = "${spring.batch.processing.interval:1000}")
    @Transactional
    public void processBatch() {
        batchProcessingTimer.record(() -> {
            try {
                List<MapRecord<String, Object, Object>> records = CircuitBreaker
                        .decorateSupplier(redisCircuitBreaker, () ->
                                redisTemplate.opsForStream()
                                        .read(Consumer.from(CONSUMER_GROUP, consumerId),
                                                StreamReadOptions.empty().count(10),
                                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()))
                        ).get();

                if (records == null || records.isEmpty()) return;

                try {
                    List<DataRecordEntity> entities = records.stream()
                            .map(record -> objectMapper.convertValue(record.getValue(), DataRecordEntity.class))
                            .collect(Collectors.toList());

                    CircuitBreaker.decorateRunnable(dbCircuitBreaker, () -> {
                        repository.saveAll(entities);
                        log.info("Processed batch size: {}", entities.size());
                        processedMessagesCounter.increment(entities.size());
                    }).run();

                    CircuitBreaker.decorateRunnable(redisCircuitBreaker, () -> {
                        records.forEach(record ->
                                redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId())
                        );
                    }).run();
                } catch (Exception e) {
                    failedMessagesCounter.increment();
                    log.error("Failed to process batch: {}", e.getMessage(), e);
                    throw e;
                }
            } catch (Exception e) {
                failedMessagesCounter.increment();
                log.error("Failed to read from Redis stream: {}", e.getMessage(), e);
            }
        });
    }

    private DataRecordEntity convertToEntity(DataRecordMessage message) {
        return DataRecordEntity.builder()
                .timestamp(Instant.ofEpochMilli(message.getTimestamp()))
                .randomValue(message.getRandomValue())
                .hashValue(message.getHashValue())
                .build();
    }

    private void handleProcessingFailure(DataRecordMessage message, Channel channel,
                                         long deliveryTag, Exception e) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, true);
                log.debug("Message nacked and requeued: {}", deliveryTag);
            } else {
                log.warn("Channel is closed, cannot nack message with delivery tag: {}", deliveryTag);
            }
        } catch (IOException ioException) {
            log.error("Error handling message failure: {}",
                    ioException.getMessage(), ioException);
        }
    }
}
