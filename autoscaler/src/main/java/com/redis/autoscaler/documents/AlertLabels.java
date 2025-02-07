package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertLabels {
    @JsonProperty("alertname")
    private RuleType ruleType;
    private String cluster;
    @JsonProperty("db")
    private String dbId;
    private String status;
    private String exporter;
    private String instance;
    private String job;
    private String node;
    private String redis;
    private String severity;
    private String slots;
    private String role;
}
