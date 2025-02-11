package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.aop.MonitorMetrics;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DataFilterService implements DataProcessor {
    private final KafkaProducerService kafkaProducer;
    private final FileWriterService fileWriter;
    private final RuleEngineService ruleEngineService;

    public DataFilterService(
            KafkaProducerService kafkaProducer,
            FileWriterService fileWriter,
            RuleEngineService ruleEngineService) {
        this.kafkaProducer = kafkaProducer;
        this.fileWriter = fileWriter;
        this.ruleEngineService = ruleEngineService;
    }

    @Override
    @MonitorMetrics(value = "data_filter", operation = "process_batch")
    public void processBatch(List<DataRecordMessage> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        try {
            List<DataRecordMessage> kafkaMessages = new ArrayList<>();
            List<DataRecordMessage> fileMessages = new ArrayList<>();

            records.forEach(record -> {
                if (ruleEngineService.evaluateRules(record)) {
                    kafkaMessages.add(record);
                } else {
                    fileMessages.add(record);
                }
            });

            if (!kafkaMessages.isEmpty()) {
                kafkaProducer.sendBatch(kafkaMessages);
                log.info("Sent batch of {} messages to database queue and mongodb queue", kafkaMessages.size());
            }

            if (!fileMessages.isEmpty()) {
                fileWriter.writeBatch(fileMessages);
                log.info("Written batch of {} messages to file", fileMessages.size());
            }

        } catch (Exception e) {
            log.error("Error processing batch of {} records: {}", records.size(), e.getMessage());
            throw e;
        }
    }
}