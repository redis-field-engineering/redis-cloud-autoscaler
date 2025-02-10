package com.redis.autoscaler;

import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.RuleRepository;
import com.redis.autoscaler.documents.TriggerType;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import com.redis.autoscaler.services.SchedulingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ScheduleStartup implements CommandLineRunner {
    private final SchedulingService schedulingService;
    private final RuleRepository ruleRepository;
    private final RedisCloudDatabaseService redisCloudDatabaseService;

    public ScheduleStartup(SchedulingService schedulingService, RuleRepository ruleRepository, RedisCloudDatabaseService redisCloudDatabaseService) {
        this.schedulingService = schedulingService;
        this.ruleRepository = ruleRepository;
        this.redisCloudDatabaseService = redisCloudDatabaseService;
    }

    @Override
    public void run(String... args) throws Exception {
        Iterable<Rule> rules = ruleRepository.findByTriggerType(TriggerType.Scheduled);
        for (Rule rule : rules) {
            schedulingService.scheduleTask(rule.getRuleId(), rule.getTriggerValue(), () -> {
                try {
                    redisCloudDatabaseService.applyRule(rule);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
