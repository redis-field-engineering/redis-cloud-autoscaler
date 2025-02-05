package com.redis.autoscaler.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redis.autoscaler.documents.AlertName;
import lombok.Data;

@Data
public class SilenceMatcher {
    private MatchType name;
    private String value;
    @JsonProperty("isRegex")
    private final boolean isRegex = false;


    public SilenceMatcher(MatchType name, String value){
        this.name = name;
        this.value = value;
    }


}