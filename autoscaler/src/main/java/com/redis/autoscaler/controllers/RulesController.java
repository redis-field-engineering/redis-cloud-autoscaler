package com.redis.autoscaler.controllers;

import com.redis.autoscaler.documents.AlertName;
import com.redis.autoscaler.documents.RuleRepository;
import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.ScaleType;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/rules")
public class RulesController {
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(RulesController.class);
    private final RuleRepository ruleRepository;

    public RulesController(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @PostMapping
    public HttpEntity<Rule> createRule(@RequestBody Rule rule) {
        LOG.info("Received request to create rule: {}", rule);

        if(rule.getRuleType() == AlertName.HighMemory || rule.getRuleType() == AlertName.LowMemory) {
            if(!rule.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }

        LOG.info("Attempting to create rule: {}", rule);
        if(ruleRepository.findByDbIdAndRuleType(rule.getDbId(), rule.getRuleType()).iterator().hasNext()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        ruleRepository.save(rule);
        return ResponseEntity.of(Optional.of(rule));
    }

    @GetMapping("/{ruleId}")
    public HttpEntity<Rule> getRule(@PathVariable String ruleId) {
        Rule rule = ruleRepository.findById(ruleId).orElse(null);
        if(rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.of(Optional.of(rule));
    }

    @DeleteMapping("/{ruleId}")
    public HttpEntity<Void> deleteRule(@PathVariable String ruleId) {
        LOG.info("Attempting to delete rule with id: {}", ruleId);
        Rule rule = ruleRepository.findById(ruleId).orElse(null);
        if(rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        ruleRepository.delete(rule);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping
    public HttpEntity<Iterable<Rule>> getRulesForDatabase(@RequestParam Optional<String> dbId) {
        if(dbId.isEmpty()) {
            return ResponseEntity.of(Optional.of(ruleRepository.findAll()));
        }
        return ResponseEntity.of(Optional.of(ruleRepository.findByDbId(dbId.get())));
    }


}
