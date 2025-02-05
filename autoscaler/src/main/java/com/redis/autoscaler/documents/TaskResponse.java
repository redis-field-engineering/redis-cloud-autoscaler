package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TaskResponse {
    private String taskId;
    private String commandType;
    private TaskStatus status;
    private String description;
    private Instant timestamp;


    public Task toTask(){
        Task taskDocument = new Task();
        taskDocument.setTaskId(this.taskId);
        taskDocument.setCommandType(this.commandType);
        taskDocument.setInitialStatus(this.status);
        taskDocument.setDescription(this.description);
        taskDocument.setInitialTimeStamp(this.timestamp);
        taskDocument.setCurrentStatus(this.status);
        taskDocument.setLastObservedTimestamp(this.timestamp);
        return taskDocument;
    }

}
