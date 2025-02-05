package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AlertStatus {
    FIRING("firing"),
    PENDING("pending"),
    SUPPRESSED("suppressed"),
    RESOLVED("resolved");

    private final String value;

    AlertStatus(String value) {
        this.value = value;
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
