package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertWebhook {
    private String receiver;
    private AlertStatus status;
    private Alert[] alerts;
    private AlertLabels commonLabels;
    private AlertAnnotations commonAnnotations;
    private String externalURL;
    private String version;
    private int truncatedAlerts;
}
