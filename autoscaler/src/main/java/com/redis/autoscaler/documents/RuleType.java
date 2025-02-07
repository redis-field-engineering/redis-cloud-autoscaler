package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RuleType {
    IncreaseMemory("IncreaseMemory"),
    DecreaseMemory("DecreaseMemory"),
    IncreaseShards("IncreaseShards"),
    DecreaseShards("DecreaseShards"),
    IncreaseThroughput("IncreaseThroughput"),
    DecreaseThroughput("DecreaseThroughput");

    private final String value;

    RuleType(String alertName) {
        this.value = alertName;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
