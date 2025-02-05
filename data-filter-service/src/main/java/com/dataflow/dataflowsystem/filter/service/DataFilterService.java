package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.aop.MonitorMetrics;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DataFilterService implements DataProcessor {
    private final MessageQueueService messageQueue;
    private final FileWriterService fileWriter;

    public DataFilterService(MessageQueueService messageQueue, FileWriterService fileWriter) {
        this.messageQueue = messageQueue;
        this.fileWriter = fileWriter;
    }

    @Override
    @MonitorMetrics(value = "processor", operation = "process_batch")
    public void processData(DataRecordMessage record) {
        try {
            if (record.getRandomValue() > 90) {
                messageQueue.send(record);
                log.info("Sent to MQ: {}", record);
            } else {
                fileWriter.write(record);
                log.info("Written to buffer: {}", record);
            }
        } catch (Exception e) {
            log.error("Error processing record {}: {}", record, e.getMessage());
            throw e;
        }
    }

    public void handleFailure(DataRecordMessage record, Throwable throwable) {
        log.warn("Fallback method invoked for record {} due to: {}", record, throwable.getMessage());
    }
}
