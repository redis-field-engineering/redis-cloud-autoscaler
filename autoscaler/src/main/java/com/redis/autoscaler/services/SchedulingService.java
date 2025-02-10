package com.redis.autoscaler.services;


import org.slf4j.Logger;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class SchedulingService{
    private static Logger LOG = org.slf4j.LoggerFactory.getLogger(SchedulingService.class);
    private final TaskScheduler taskScheduler;
    private final Map<String,ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public SchedulingService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public String scheduleTask(String id, String cronExpression, Runnable task){
        if(scheduledTasks.containsKey(id)){
            LOG.info("Task with id {} already exists", id);
            return "Task with id " + id + " already exists";
        }

        ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(task, new CronTrigger(cronExpression));
        scheduledTasks.put(id, scheduledFuture);
        return "Scheduled task with id " + id;
    }

    public String cancelTask(String id){
        ScheduledFuture<?> scheduledFuture = scheduledTasks.get(id);
        if(scheduledFuture == null){
            LOG.info("Task with id {} does not exist", id);
            return "Task with id " + id + " does not exist";
        }

        scheduledFuture.cancel(true);
        scheduledTasks.remove(id);
        return "Cancelled task with id " + id;
    }
}
