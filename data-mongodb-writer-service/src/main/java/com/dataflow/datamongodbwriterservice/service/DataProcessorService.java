package com.dataflow.datamongodbwriterservice.service;


import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class DataProcessorService {
    private final DataRecordMongoRepository mongoRepo;

    public DataProcessorService(DataRecordMongoRepository mongoRepo) {
        this.mongoRepo = mongoRepo;
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}")
    public void processMessage(DataRecord record) {
        log.info("Received message: {}", record);
        String hashValue = record.getHashValue();
        Optional<DataRecordDocument> lastRecord = mongoRepo.findFirstByOrderByTimestampDesc();

        if (lastRecord.isPresent() &&
                Integer.parseInt(hashValue, 16) > Integer.parseInt("99", 16)) {
            DataRecordDocument lastDoc = lastRecord.get();
            lastDoc.getNestedRecords().add(DataRecordDocument.fromDataRecord(record));
            log.info("Updated record: {}", lastDoc);
            mongoRepo.save(lastDoc);
        } else {
            log.info("Created new record: {}", record);
            mongoRepo.save(DataRecordDocument.fromDataRecord(record));
        }
    }
}