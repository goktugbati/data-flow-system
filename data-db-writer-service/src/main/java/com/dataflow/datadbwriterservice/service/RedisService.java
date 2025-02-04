package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.aop.MonitorMetrics;
import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    private final RedisTemplate<String, DataRecordMessage> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisService(RedisTemplate<String, DataRecordMessage> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
    public void writeToStream(String jsonMessage) {
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("message", jsonMessage);

            RecordId recordId = redisTemplate.opsForStream()
                    .add(Record.of(fields).withStreamKey(STREAM_KEY));

            log.debug("Written to Redis stream with ID: {}", recordId);
        } catch (Exception e) {
            log.error("Failed to write to Redis stream", e);
        }
    }

    @CircuitBreaker(name = SERVICE_NAME)
    @MonitorMetrics(value = "redis", operation = "read_from_stream")
    public List<MapRecord<String, Object, Object>> readFromStream() {
        try {
            Consumer consumer = Consumer.from(CONSUMER_GROUP, "consumer-1");

            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                    .read(consumer, StreamReadOptions.empty().count(10),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) {
                return Collections.emptyList();
            }

            return records;
        } catch (Exception e) {
            log.error("Failed to read from Redis stream", e);
            throw e;
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
