package com.dataflow.datamongodbwriterservice.service;


import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
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
    public void processMessage(DataRecordMessage record) {
        log.info("Received message: {}", record);
        String hashValue = record.getHashValue();
        Optional<DataRecordDocument> lastRecord = mongoRepo.findFirstByOrderByTimestampDesc();

        if (lastRecord.isPresent() && hashValue.compareTo("99") > 0) {
            DataRecordDocument lastDoc = lastRecord.get();
            lastDoc.getNestedRecords().add(DataRecordDocument.fromDataRecord(record));
            log.info("Updated record with nested data: {}", lastDoc);
            mongoRepo.save(lastDoc);
        } else {
            DataRecordDocument newDoc = DataRecordDocument.fromDataRecord(record);
            log.info("Creating new record: {}", newDoc);
            mongoRepo.save(newDoc);
        }
    }
}
