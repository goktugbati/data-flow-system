package com.dataflow.datamongodbwriterservice.service;


import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
import com.mongodb.MongoException;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

@Service
@Slf4j
public class DataProcessorService {
    private final DataRecordMongoRepository mongoRepo;

    public DataProcessorService(DataRecordMongoRepository mongoRepo) {
        this.mongoRepo = mongoRepo;
    }

    @RabbitListener(queues = "${rabbitmq.queue-name}", ackMode = "MANUAL", concurrency = "3-10")
    public void processMessage(DataRecordMessage record, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.debug("Received message: {}", record);
        try {
            String messageId = generateMessageId(record);
            if (mongoRepo.existsById(messageId)) {
                log.info("Message already processed: {}", record);
                channel.basicAck(deliveryTag, false);
                return;
            }
            Optional<DataRecordDocument> lastRecord = mongoRepo.findFirstByOrderByTimestampDesc();
            if (lastRecord.isPresent() && record.getHashValue().compareTo("99") > 0) {
                DataRecordDocument lastDoc = lastRecord.get();
                if (lastDoc.getNestedRecords() == null) {
                    lastDoc.setNestedRecords(new ArrayList<>());
                }
                lastDoc.getNestedRecords().add(DataRecordDocument.fromDataRecord(record, messageId));
                log.info("Updated record with nested data: {}", lastDoc);
                saveDocument(lastDoc);
            } else {
                DataRecordDocument newDoc = DataRecordDocument.fromDataRecord(record, messageId);
                log.info("Creating new record: {}", newDoc);
                saveDocument(newDoc);
            }

            channel.basicAck(deliveryTag, false);
            log.debug("Message acknowledged: {}", deliveryTag);

        } catch (Exception e) {
            log.error("Error processing message: {}", record, e);

            try {
                channel.basicNack(deliveryTag, false, true);
                log.debug("Message nacked and requeued: {}", deliveryTag);
            } catch (IOException ioException) {
                log.error("Error during message nack: {}", ioException.getMessage(), ioException);
            }
        }
    }

    @Retryable(
            value = { MongoException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    private void saveDocument(DataRecordDocument document) {
        mongoRepo.save(document);
    }

    private String generateMessageId(DataRecordMessage record) {
        return record.getHashValue() + ":" + record.getTimestamp();
    }
}
