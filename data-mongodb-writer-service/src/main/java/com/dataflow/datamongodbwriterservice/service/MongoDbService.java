package com.dataflow.datamongodbwriterservice.service;

import com.dataflow.datamongodbwriterservice.aop.MonitorMetrics;
import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class MongoDbService {
    private final DataRecordMongoRepository repository;

    public MongoDbService(DataRecordMongoRepository repository) {
        this.repository = repository;
    }
    @CircuitBreaker(name = "mongoDbCircuitBreaker", fallbackMethod = "fallbackWrite")
    @MonitorMetrics(value = "mongodb", operation = "write")
    public void processAndSaveRecord(DataRecordMessage record) {
        String messageId = generateMessageId(record);
        if (repository.existsById(messageId)) {
            log.info("Message already processed: {}", record);
            return;
        }

        Optional<DataRecordDocument> lastRecord = repository.findFirstByOrderByTimestampDesc();
        if (lastRecord.isPresent() && shouldNest(record)) {
            updateExistingRecord(lastRecord.get(), record, messageId);
        } else {
            createNewRecord(record, messageId);
        }
    }

    private void updateExistingRecord(DataRecordDocument lastDoc, DataRecordMessage record, String messageId) {
        DataRecordDocument nestedRecord = DataRecordDocument.fromDataRecord(record, messageId);
        try {
            repository.addNestedRecord(lastDoc.getId(), nestedRecord);
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
            repository.insert(newDoc);
            log.info("Created new record with id: {}", messageId);
        } catch (Exception e) {
            log.error("Error creating new document with id {}: {}", messageId, e.getMessage());
            throw e;
        }
    }

    public void fallbackWrite(DataRecordMessage message, Throwable throwable) {
        log.error("MongoDB write failed, executing fallback. Error: {}", throwable.getMessage());
    }

    private String generateMessageId(DataRecordMessage record) {
        return record.getHashValue() + ":" + record.getTimestamp();
    }
}
