package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertAnnotations {
    private String description;
    private String summary;
}
