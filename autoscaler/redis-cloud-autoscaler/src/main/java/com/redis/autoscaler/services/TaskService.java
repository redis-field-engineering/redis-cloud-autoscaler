package com.redis.autoscaler.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.Constants;
import com.redis.autoscaler.HttpClientConfig;
import com.redis.autoscaler.documents.Task;
import com.redis.autoscaler.documents.TaskRepository;
import com.redis.autoscaler.documents.TaskResponse;
import com.redis.autoscaler.documents.TaskStatus;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
public class TaskService {
    private final HttpClient httpClient;
    private final HttpClientConfig httpClientConfig;
    private final TaskRepository taskRepository;
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public TaskService(HttpClient httpClient, HttpClientConfig httpClientConfig, TaskRepository taskRepository) {
        this.httpClient = httpClient;
        this.httpClientConfig = httpClientConfig;
        this.taskRepository = taskRepository;
    }


    @SneakyThrows
    public Optional<Task> pendingTaskForDb (String dbId) {
        Iterator<Task> pendingTasks = taskRepository.findByDbIdAndCurrentStatusIn(dbId, Constants.CURRENTLY_ACTIVE_TASK_STATUSES).iterator();
        if(!pendingTasks.hasNext()){
            return Optional.empty();
        }

        // now we need to check if the task is still pending

        Task task = pendingTasks.next();
        URI uri = new URI(Constants.REDIS_CLOUD_URI_BASE +  "/tasks/" + task.getTaskId());
        HttpRequest request = httpClientConfig.requestBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        TaskResponse taskResponse = objectMapper.readValue(response.body(), TaskResponse.class);


        task.setLastObservedTimestamp(java.time.Instant.now());
        task.setDescription(taskResponse.getDescription());
        task.setCurrentStatus(taskResponse.getStatus());
        taskRepository.save(task);
        return Optional.of(task);
    }

}
