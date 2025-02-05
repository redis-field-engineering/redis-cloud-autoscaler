package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    initialized("initialized"),
    received("received"),
    processingInProgress("processing-in-progress"),
    processingCompleted("processing-completed"),
    processingError("processing-error");



    private String value;
    TaskStatus(String value){
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
