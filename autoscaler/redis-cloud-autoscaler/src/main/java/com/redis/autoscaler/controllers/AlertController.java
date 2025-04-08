package com.redis.autoscaler.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.RedisCloudDatabase;
import com.redis.autoscaler.documents.*;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import com.redis.autoscaler.services.SilencerService;
import com.redis.autoscaler.services.TaskService;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final int SILENCE_DURATION = 1;
    private final RuleRepository ruleRepository;
    private final TaskRepository taskRepository;
    private final RedisCloudDatabaseService redisCloudDatabaseService;
    private final TaskService taskService;
    private static ObjectMapper objectMapper = createObjectMapper();
    private final SilencerService silencerService;

    public AlertController(RuleRepository ruleRepository, TaskRepository taskRepository, RedisCloudDatabaseService redisCloudDatabaseService, TaskService taskService, SilencerService silencerService) {

        this.ruleRepository = ruleRepository;
        this.taskRepository = taskRepository;
        this.redisCloudDatabaseService = redisCloudDatabaseService;
        this.taskService = taskService;
        this.silencerService = silencerService;
    }


    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public static Logger LOG = org.slf4j.LoggerFactory.getLogger(AlertController.class);

    @PostMapping
    public ResponseEntity<Map<String,Task>> inboundAlert(@RequestBody String jsonBody) throws IOException, InterruptedException {
        LOG.info("Received alert: {}", jsonBody);
        AlertWebhook webhook = objectMapper.readValue(jsonBody, AlertWebhook.class);
        Map<String,Task> taskMap = new HashMap<>();

        for(Alert alert: webhook.getAlerts()){
            // 0. Skip if alert is not firing
            if(alert.getStatus() != AlertStatus.FIRING){
                LOG.info("Received Alert for dbId: {} with status: {}. Skipping (since it's not firing)", alert.getLabels().getDbId(), alert.getStatus());
                continue;
            }

            // 1. Extract alert type and dbId
            String dbId = alert.getLabels().getDbId();
            RuleType ruleType = alert.getLabels().getRuleType();

            RedisCloudDatabase materializedDb;
            Optional<RedisCloudDatabase> dbOpt = redisCloudDatabaseService.getDatabase(dbId);
            if(dbOpt.isEmpty()){
                // We could not find the db directly from its ID in the CAPI, perhaps it's an A-A database, we only have one of the locals, let's try looking it up via the instance name.
                dbOpt = redisCloudDatabaseService.getDatabaseByInternalInstanceName(alert.getLabels().getInstance());
                if(dbOpt.isEmpty()){
                    LOG.info("No database found for dbId: {} and alertName: {} JSON Body: {}", dbId, ruleType, jsonBody);
                    continue;
                }

                materializedDb = dbOpt.get();
                dbId = String.valueOf(materializedDb.getDatabaseId());
            }

            if(taskMap.containsKey(dbId)){
                LOG.info("Scaling task already in progress for dbId: {}", dbId);
                silencerService.silenceAlert(alert.getLabels().getInstance(), ruleType, SILENCE_DURATION);
                continue; // move onto next alert
            }

            // 2. Check if there are any pending scaling tasks for Database
            Optional<Task> pendingTaskOption = taskService.pendingTaskForDb(dbId);
            if(pendingTaskOption.isPresent() && pendingTaskOption.get().getCurrentStatus() == TaskStatus.processingInProgress){
                LOG.info("Scaling task already in progress for dbId: {}", dbId);
                taskMap.put(dbId, pendingTaskOption.get());
                silencerService.silenceAlert(alert.getLabels().getInstance(), ruleType, SILENCE_DURATION);
                continue; // move onto next alert
            }

            // 3. Find Rules associated alert type and DB ID
            Iterable<Rule> rules = ruleRepository.findByDbIdAndRuleTypeAndTriggerType(dbId, ruleType, TriggerType.Webhook);
            if(!rules.iterator().hasNext()){
                LOG.info("No rule found for dbId: {} and alertName: {} JSON Body: {}", dbId, ruleType, jsonBody);
                continue; // move onto next alert
            }

            // 4. If not, run scaling
            Rule rule = rules.iterator().next();
            Optional<Task> res = redisCloudDatabaseService.applyRule(rule);
            if(res.isEmpty()){
                LOG.info("Failed to apply rule for dbId: {} and alertName: {}", dbId, ruleType);
                continue;
            }

            silencerService.silenceAlert(alert.getLabels().getInstance(), ruleType, SILENCE_DURATION);


            // 5. Save task data structure along with other metadata while pending
            Task task = res.get();

            LOG.info("Saving task: {}", task);
            taskRepository.save(task);

            taskMap.put(dbId, task);

            LOG.info("Alert status {} ", alert.getStatus());
        }

        if(taskMap.isEmpty()){
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.of(Optional.of(taskMap));
    }
}
