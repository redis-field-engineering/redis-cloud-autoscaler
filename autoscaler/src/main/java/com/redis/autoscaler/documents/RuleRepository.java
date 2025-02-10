package com.redis.autoscaler.documents;

import com.redis.om.spring.repository.RedisDocumentRepository;

public interface RuleRepository extends RedisDocumentRepository<Rule, String> {
    Iterable<Rule> findByDbIdAndRuleTypeAndTriggerType(String dbId, RuleType ruleType, TriggerType triggerType);
    Iterable<Rule> findByDbId(String dbId);
    Iterable<Rule> findByTriggerType(TriggerType triggerType);
}
