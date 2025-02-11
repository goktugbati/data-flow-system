package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.aop.MonitorMetrics;
import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.datadbwriterservice.repository.DataRecordRepository;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataProcessorService {
    private final RedisService redisService;
    private final DataRecordRepository repository;


    public DataProcessorService(RedisService redisService, DataRecordRepository repository) {
        this.redisService = redisService;
        this.repository = repository;
    }

    @KafkaListener(
            topics = "${kafka.topic.name}",  // Updated to use shared topic
            containerFactory = "kafkaListenerContainerFactory"
    )
    @MonitorMetrics(value = "processor", operation = "kafka_to_redis")
    public void processKafkaMessages(
            @Payload List<DataRecordMessage> messages,
            @Header(KafkaHeaders.ACKNOWLEDGMENT) Acknowledgment acknowledgment) {
        try {
            if (CollectionUtils.isEmpty(messages)) {
                acknowledgment.acknowledge();
                return;
            }

            log.debug("Received batch of {} messages from Kafka", messages.size());
            redisService.writeToStreamBatch(messages);
            acknowledgment.acknowledge();
            log.debug("Successfully wrote {} messages to Redis stream", messages.size());
        } catch (Exception e) {
            log.error("Failed to process Kafka message batch: {}", e.getMessage());
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "${spring.batch.processing.interval:1000}")
    @Transactional
    @MonitorMetrics(value = "processor", operation = "redis_to_db")
    public void processRedisToDatabase() {
        try {
            List<MapRecord<String, Object, Object>> records = redisService.readFromStream();

            if (records.isEmpty()) {
                return;
            }

            List<DataRecordEntity> entities = redisService.convertToEntities(records);

            if (!entities.isEmpty()) {
                repository.saveAll(entities);

                List<RecordId> recordIds = records.stream()
                        .map(MapRecord::getId)
                        .collect(Collectors.toList());

                redisService.acknowledgeMessages(recordIds);
                log.info("Successfully processed batch of {} records from Redis to database", entities.size());
            }
        } catch (Exception e) {
            log.error("Failed to process Redis to database batch: {}", e.getMessage());
        }
    }
}