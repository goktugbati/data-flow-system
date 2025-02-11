package com.dataflow.datamongodbwriterservice.service;

import com.dataflow.datamongodbwriterservice.rules.HashValueRule;
import com.dataflow.model.DataRecordMessage;
import jakarta.annotation.PostConstruct;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
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
        rules.register(new HashValueRule());
    }


    public boolean evaluateRules(DataRecordMessage message) {
        Facts facts = new Facts();
        facts.put("hashValue", message.getHashValue());

        rulesEngine.fire(rules, facts);
        return facts.get("hashValue") != null;
    }


}
