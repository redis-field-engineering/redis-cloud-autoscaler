package com.redis.autoscaler.documents;

import com.redis.om.spring.repository.RedisDocumentRepository;

import java.util.List;

public interface TaskRepository extends RedisDocumentRepository<Task, String> {
    Iterable<Task> findByDbId(String dbId);
    Iterable<Task> findByCurrentStatusIn(List<TaskStatus> currentStatus);
    Iterable<Task> findByDbIdAndCurrentStatusIn(String dbId, List<TaskStatus> currentStatus);
    Iterable<Task> findByDbIdAndCurrentStatusNotIn(String dbId, List<TaskStatus> currentStatus);
}
