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
    private final RuleEngineService ruleEngineService;

    public DataFilterService(MessageQueueService messageQueue, FileWriterService fileWriter, RuleEngineService ruleEngineService) {
        this.messageQueue = messageQueue;
        this.fileWriter = fileWriter;
        this.ruleEngineService = ruleEngineService;
    }

    @Override
    @MonitorMetrics(value = "data_filter", operation = "process_data")
    public void processData(DataRecordMessage record) {
        try {
            if (ruleEngineService.evaluateRules(record)) {
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
}
