package com.redis.autoscaler.controllers;

import com.redis.autoscaler.Constants;
import com.redis.autoscaler.documents.Task;
import com.redis.autoscaler.documents.TaskRepository;
import com.redis.autoscaler.services.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Optional;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskRepository taskRepository;
    private final TaskService taskService;

    public TaskController(TaskRepository taskRepository, TaskService taskService) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
    }

    @GetMapping("/pending")
    public Iterable<Task> getPendingTasks(@RequestParam Optional<String> dbId){
        if(dbId.isEmpty()){
            return taskRepository.findByCurrentStatusIn(Constants.CURRENTLY_ACTIVE_TASK_STATUSES);
        }

        Optional<Task> pendingTaskForDb = taskService.pendingTaskForDb(dbId.get());
        if(pendingTaskForDb.isEmpty()){
            return Collections.emptyList();
        }

        return Collections.singletonList(pendingTaskForDb.get());
    }
}
