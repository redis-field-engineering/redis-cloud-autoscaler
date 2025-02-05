package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ScaleType {
    Step("Step"),
    Deterministic("Deterministic"),
    Exponential("Exponential");

    private final String value;
    ScaleType(String value){
        this.value = value;
    }

    @JsonValue
    public String getValue(){
        return value;
    }
}
