package com.redis.autoscaler.controllers;

import com.redis.autoscaler.documents.RuleRepository;
import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.TriggerType;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import com.redis.autoscaler.services.SchedulingService;
import org.slf4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/rules")
public class RulesController {
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(RulesController.class);
    private final RedisCloudDatabaseService redisCloudDatabaseService;
    private final RuleRepository ruleRepository;
    private final SchedulingService schedulingService;

    public RulesController(RedisCloudDatabaseService redisCloudDatabaseService, RuleRepository ruleRepository, SchedulingService schedulingService) {
        this.redisCloudDatabaseService = redisCloudDatabaseService;
        this.ruleRepository = ruleRepository;
        this.schedulingService = schedulingService;
    }

    @GetMapping("/numDatabases")
    public HttpEntity<Integer> getNumDatabases() throws IOException, InterruptedException {
        return ResponseEntity.ok(redisCloudDatabaseService.getDatabaseCount());
    }


    @PostMapping
    public HttpEntity<Object> createRule(@RequestBody Rule rule) {
        LOG.info("Received request to create rule: {}", rule);

        String ruleValidityError = rule.getValidationError();
        if(!ruleValidityError.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ruleValidityError);
        }

        LOG.info("Attempting to create rule: {}", rule);
        if(ruleRepository.findByDbIdAndRuleTypeAndTriggerType(rule.getDbId(), rule.getRuleType(), rule.getTriggerType()).iterator().hasNext()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        if(rule.getTriggerType() == TriggerType.Scheduled){
            try {
                CronExpression.parse(rule.getTriggerValue());
                ruleRepository.save(rule);
                schedulingService.scheduleTask(rule.getRuleId(), rule.getTriggerValue(), () -> {
                    try {
                        redisCloudDatabaseService.applyRule(rule);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                LOG.error("Invalid cron expression: {} {}", rule.getTriggerValue(), e.toString());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid cron expression: " + rule.getTriggerValue());
            }
        }
        else{
            ruleRepository.save(rule);
        }

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

        if(rule.getTriggerValue() != null && rule.getTriggerType() == TriggerType.Scheduled) {
            schedulingService.cancelTask(rule.getRuleId());
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
