package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.aop.MonitorMetrics;
import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisService {
    private static final String SERVICE_NAME = "redisService";
    private static final String STREAM_KEY = "data:stream";
    private static final String CONSUMER_GROUP = "db-writers";
    private static final String FIELD_NAME = "message";
    private static final String CONSUMER_NAME = UUID.randomUUID().toString();

    @Value("${spring.redis.stream.batch-size:100}")
    private int batchSize;

    private final RedisTemplate<String, DataRecordMessage> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StreamOperations<String, Object, Object> streamOperations;

    public RedisService(RedisTemplate<String, DataRecordMessage> redisTemplate,
                        ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.streamOperations = redisTemplate.opsForStream();
    }

    @PostConstruct
    public void ensureStreamExists() {
        try {
            Boolean streamExists = redisTemplate.hasKey(STREAM_KEY);
            if (Boolean.FALSE.equals(streamExists)) {
                log.info("Creating Redis Stream: {}", STREAM_KEY);
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            }
        } catch (Exception e) {
            log.error("Error ensuring Redis Stream exists: {}", e.getMessage(), e);
        }
    }

    @CircuitBreaker(name = SERVICE_NAME)
    @MonitorMetrics(value = "redis", operation = "write_to_stream")
    public void writeToStreamBatch(List<DataRecordMessage> records) {
        if (records.isEmpty()) {
            return;
        }

        try {
            List<Record<String, ?>> redisRecords = records.stream()
                    .map(this::convertToRedisRecord)
                    .collect(Collectors.toList());

            for (Record<String, ?> record : redisRecords) {
                streamOperations.add(record);
            }
            log.debug("Successfully wrote batch of {} records to Redis stream", records.size());
        } catch (Exception e) {
            log.error("Failed to write batch to Redis stream: {}", e.getMessage());
            throw new RuntimeException("Failed to write to Redis stream", e);
        }
    }

    private MapRecord<String, String, String> convertToRedisRecord(DataRecordMessage message) {
        try {
            Map<String, String> fieldMap = new HashMap<>();
            fieldMap.put("message", objectMapper.writeValueAsString(message));
            return MapRecord.create(STREAM_KEY, fieldMap);
        } catch (Exception e) {
            log.error("Failed to convert message to Redis record: {}", e.getMessage());
            throw new RuntimeException("Failed to convert message", e);
        }
    }

    @CircuitBreaker(name = SERVICE_NAME)
    @Retry(name = SERVICE_NAME)
    @MonitorMetrics(value = "redis", operation = "read_batch")
    public List<MapRecord<String, Object, Object>> readFromStream() {
        try {
            Consumer consumer = Consumer.from(CONSUMER_GROUP, CONSUMER_NAME);
            StreamReadOptions readOptions = StreamReadOptions.empty()
                    .block(Duration.ofMillis(100))
                    .count(batchSize);

            List<MapRecord<String, Object, Object>> records = streamOperations.read(
                    consumer,
                    readOptions,
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            return records != null ? records : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to read from Redis stream: {}", e.getMessage());
            throw new RedisSystemException("Failed to read from Redis stream", e);
        }
    }

    @CircuitBreaker(name = SERVICE_NAME)
    @MonitorMetrics(value = "redis", operation = "acknowledge_messages")
    public void acknowledgeMessages(List<RecordId> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return;
        }

        try {
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, recordIds.toArray(new RecordId[0]));

            log.debug("Acknowledged {} messages", recordIds.size());
        } catch (Exception e) {
            log.error("Failed to acknowledge messages", e);
            throw e;
        }
    }


    public List<DataRecordEntity> convertToEntities(List<MapRecord<String, Object, Object>> records) {
        return records.stream()
                .map(record -> {
                    try {
                        String json = (String) record.getValue().get("message");

                        DataRecordMessage message = objectMapper.readValue(json, DataRecordMessage.class);

                        DataRecordEntity entity = new DataRecordEntity();
                        entity.setHashValue(message.getHashValue());
                        entity.setTimestamp(Instant.ofEpochMilli(message.getTimestamp()));
                        entity.setRandomValue(message.getRandomValue());

                        return entity;
                    } catch (Exception e) {
                        log.error("Failed to deserialize Redis message: {}", record, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
