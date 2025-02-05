package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Alert {
    private AlertStatus status;
    private AlertLabels labels;
    private AlertAnnotations annotations;
    private Instant startsAt;
    private Instant endsAt;
    private String generatorURL;
    private String fingerprint;
}
