package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.datadbwriterservice.repository.DataRecordRepository;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public DataProcessorService(DataRecordRepository repository,
                                RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.consumerId = UUID.randomUUID().toString();
    }

    @PostConstruct
    public void init() {
        try {
            RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
            connection.streamCommands().xGroupCreate(
                    STREAM_KEY.getBytes(),
                    CONSUMER_GROUP,
                    ReadOffset.from("0"),
                    true
            );
        } catch (Exception e) {
            log.warn("Stream init error (safe to ignore if already exists): {}", e.getMessage());
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}")
    public void processMessage(DataRecordMessage message) {
        try {
            DataRecordEntity record = convertToEntity(message);
            redisTemplate.opsForStream().add(
                    Record.of(objectMapper.convertValue(record, Map.class))
                            .withStreamKey(STREAM_KEY)
            );
            log.debug("Added to stream: {}", record);
        } catch (Exception e) {
            log.error("Stream write error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${batch.processing.interval:1000}")
    @Transactional
    public void processBatch() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(Consumer.from(CONSUMER_GROUP, consumerId),
                            StreamReadOptions.empty().count(10),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            List<DataRecordEntity> entities = records.stream()
                    .map(record -> objectMapper.convertValue(record.getValue(), DataRecordEntity.class))
                    .collect(Collectors.toList());

            repository.saveAll(entities);
            log.info("Processed batch size: {}", entities.size());

            records.forEach(record ->
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId())
            );
        } catch (Exception e) {
            log.error("Batch processing error: {}", e.getMessage(), e);
        }
    }

    private DataRecordEntity convertToEntity(DataRecordMessage message) {
        return DataRecordEntity.builder()
                .timestamp(Instant.ofEpochMilli(message.getTimestamp()))
                .randomValue(message.getRandomValue())
                .hashValue(message.getHashValue())
                .build();
    }
}
