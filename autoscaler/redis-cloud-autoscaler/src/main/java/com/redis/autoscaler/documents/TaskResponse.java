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
    private TaskResponse response;


    public Task toTask(){
        Task taskDocument = new Task();
        taskDocument.setTaskId(this.taskId);
        taskDocument.setCommandType(this.commandType);
        taskDocument.setInitialStatus(this.status);
        taskDocument.setDescription(this.description);
        taskDocument.setInitialTimeStamp(this.timestamp);
        taskDocument.setCurrentStatus(this.status);
        taskDocument.setLastObservedTimestamp(this.timestamp);
        taskDocument.setResponse(this.response);
        return taskDocument;
    }

    @Data
    public static class Response{
        private int resourceId;
        private int additionalResourceId;
        private String error;
        private String additionalInfo;
    }

}
