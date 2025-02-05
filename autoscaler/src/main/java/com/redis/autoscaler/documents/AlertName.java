package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertName {
    HighMemory("HighMemory"),
    LowMemory("LowMemory"),
    HighLatency("HighLatency"),
    LowLatency("LowLatency"),
    HighThroughput("HighThroughput"),
    LowThroughput("LowThroughput");

    private final String value;

    AlertName(String alertName) {
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
