package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseListResponse {
    private int accountId;
    private Subscripition[] subscription;

}
