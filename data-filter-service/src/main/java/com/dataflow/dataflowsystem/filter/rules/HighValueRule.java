package com.dataflow.dataflowsystem.filter.rules;

import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import lombok.extern.slf4j.Slf4j;

@Rule(name = "High Value Rule", description = "Filters values greater than 90")
@Slf4j
public class HighValueRule {

    @Condition
    public boolean isHighValue(@Fact("value") int value) {
        return value > 90;
    }

    @Action
    public void processHighValue() {
        log.info("High value detected! Sending to RabbitMQ.");
    }
}
