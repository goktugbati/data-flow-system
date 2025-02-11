package com.dataflow.datamongodbwriterservice.service;

import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.api.RulesEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MongoDbService {

    private final DataRecordMongoRepository repository;
    private final RuleEngineService ruleEngineService;

    public MongoDbService(DataRecordMongoRepository repository, RuleEngineService ruleEngineService) {
        this.repository = repository;
        this.ruleEngineService = ruleEngineService;
    }

    public void processBatch(List<DataRecordMessage> messages) {
        try {
            List<DataRecordDocument> newDocuments = new ArrayList<>();

            for (DataRecordMessage message : messages) {

                if (ruleEngineService.evaluateRules(message)) {
                    repository.findFirstByOrderByTimestampDesc().ifPresentOrElse(
                            existingDoc -> {
                                existingDoc.getNestedRecords().add(DataRecordDocument.fromDataRecord(message));
                                newDocuments.add(existingDoc);
                            },
                            () -> {
                                DataRecordDocument newDoc = DataRecordDocument.fromDataRecord(message);
                                newDocuments.add(newDoc);
                            }
                    );
                } else {
                    DataRecordDocument newDoc = DataRecordDocument.fromDataRecord(message);
                    newDocuments.add(newDoc);
                }
            }

            repository.saveAll(newDocuments);
            log.info("Batch of {} documents processed and saved.", newDocuments.size());

        } catch (Exception e) {
            log.error("Error processing batch: {}", e.getMessage(), e);
        }
    }
}
