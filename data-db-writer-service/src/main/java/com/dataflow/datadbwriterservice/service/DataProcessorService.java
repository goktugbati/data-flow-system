package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import com.dataflow.datadbwriterservice.repository.DataRecordRepository;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class DataProcessorService {
    private final DataRecordRepository repository;

    public DataProcessorService(DataRecordRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}")
    @Transactional
    public void processMessage(DataRecordMessage message) {
        log.debug("Received message: {}", message);

        DataRecordEntity record = new DataRecordEntity();
        record.setTimestamp(Instant.ofEpochMilli(message.getTimestamp()));
        record.setRandomValue(message.getRandomValue());
        record.setHashValue(message.getHashValue());

        repository.save(record);
        log.debug("Saved record to database: {}", record);
    }
}
