package com.redis.autoscaler.requests;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MatchType {
    AlertName("alertname"),
    Instance("instance"),
    Severity("severity");

    private final String value;

    MatchType(String value){
        this.value = value;
    }

    @JsonValue
    public String getValue(){
        return value;
    }
}
