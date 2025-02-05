package com.redis.autoscaler;

import com.redis.autoscaler.documents.TaskStatus;

import java.util.List;

public class Constants {
    public static final String HighMemoryUsageAlert = "HighMemoryUsage";
    public static final String HighCpuUsageAlert = "HighCpu";
    public static final String HighThroughputAlert = "HighThroughput";
    public static final String HighLatencyAlert = "HighLatency";
    public static final String REDIS_CLOUD_URI_BASE = "https://api.redislabs.com/v1";
    public static final List<TaskStatus> CURRENTLY_ACTIVE_TASK_STATUSES = List.of(TaskStatus.received, TaskStatus.initialized, TaskStatus.processingInProgress);
}
