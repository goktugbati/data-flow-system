package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.aop.MonitorMetrics;
import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.datadbwriterservice.repository.DataRecordRepository;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DataProcessorService {

    private final RedisService redisService;
    private final MessageBrokerService messageBrokerService;
    private final DataRecordRepository repository;

    public DataProcessorService(RedisService redisService, MessageBrokerService messageBrokerService, DataRecordRepository repository) {
        this.redisService = redisService;
        this.messageBrokerService = messageBrokerService;
        this.repository = repository;
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}")
    @MonitorMetrics(value = "processor", operation = "process_message")
    public void processMessage(Message message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            String jsonMessage = new String(message.getBody(), StandardCharsets.UTF_8);
            redisService.writeToStream(jsonMessage);
            messageBrokerService.acknowledgeMessage(channel, deliveryTag);
            log.debug("Successfully processed JSON message and wrote to Redis stream");
        } catch (Exception e) {
            log.error("Failed to process message: {}", message, e);
            messageBrokerService.rejectMessage(channel, deliveryTag, true);
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "${spring.batch.processing.interval:1000}")
    @Transactional
    @MonitorMetrics(value = "processor", operation = "process_batch")
    public void processBatch() {
        List<MapRecord<String, Object, Object>> records = redisService.readFromStream();

        if (records.isEmpty()) {
            return;
        }

        try {
            List<DataRecordEntity> entities = redisService.convertToEntities(records);
            repository.saveAll(entities);

            List<RecordId> recordIds = records.stream()
                    .map(MapRecord::getId)
                    .collect(Collectors.toList());

            redisService.acknowledgeMessages(recordIds);

            log.info("Successfully processed batch of {} records", records.size());
        } catch (Exception e) {
            log.error("Failed to process batch of {} records", records.size(), e);
            throw e;
        }
    }
}