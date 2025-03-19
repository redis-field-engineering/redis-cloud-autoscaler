package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Subscripition {
    private String subscriptionId;
    private int numberOfDatabases;
    private RedisCloudDatabase[] databases;
}
