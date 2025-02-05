package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.rules.HighValueRule;
import com.dataflow.model.DataRecordMessage;
import jakarta.annotation.PostConstruct;
import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.stereotype.Service;


@Service
public class RuleEngineService {

    private Rules rules;
    private RulesEngine rulesEngine;

    @PostConstruct
    public void init() {
        rules = new Rules();
        rulesEngine = new DefaultRulesEngine();
        rules.register(new HighValueRule());
    }

    public boolean evaluateRules(DataRecordMessage message) {
        Facts facts = new Facts();
        facts.put("value", message.getRandomValue());

        rulesEngine.fire(rules, facts);

        Integer evaluatedValue = facts.get("value");
        return evaluatedValue != null && evaluatedValue > 90;
    }
}
