package com.redis.autoscaler.documents;

import com.redis.autoscaler.ScaleRequest;
import com.redis.om.spring.annotations.Document;
import com.redis.om.spring.annotations.Indexed;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@Document
public class Task {
    @Indexed private String dbId;
    @Id private String taskId;
    private String commandType;
    private TaskStatus initialStatus;
    @Indexed private TaskStatus currentStatus;
    private String description;
    @Indexed private Instant initialTimeStamp;
    @Indexed private Instant lastObservedTimestamp;
    private ScaleRequest scaleRequest;
    private TaskResponse Response;
}
