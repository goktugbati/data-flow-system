package com.dataflow.datamongodbwriterservice.rules;

import lombok.extern.slf4j.Slf4j;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;

@Rule(name = "High Value Rule", description = "Filters values greater than 90")
@Slf4j
public class HashValueRule {

    @Condition
    public boolean checkRecordHashValue(@Fact("hashValue") String hashValue) {
        return hashValue.compareTo("99") > 0;
    }


    @Action
    public void processRecord() {
        log.info("High value detected! Sending to RabbitMQ.");
    }
}
