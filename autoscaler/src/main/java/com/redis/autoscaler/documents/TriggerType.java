package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TriggerType {
    Webhook("webhook"),
    Scheduled("scheduled");

    private final String value;
    TriggerType(String value){
        this.value = value;
    }

    @JsonValue
    public String getValue(){
        return value;
    }

    @Override
    public String toString(){
        return value;
    }
}
