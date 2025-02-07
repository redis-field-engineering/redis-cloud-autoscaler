package com.redis.autoscaler.documents;

import com.redis.om.spring.repository.RedisDocumentRepository;

public interface RuleRepository extends RedisDocumentRepository<Rule, String> {
    Iterable<Rule> findByDbIdAndRuleType(String dbId, RuleType ruleType);
    Iterable<Rule> findByDbId(String dbId);
}
