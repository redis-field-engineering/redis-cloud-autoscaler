package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedisCloudDatabase {

    private int databaseId;
    private String name;
    private double datasetSizeInGb;
    private int memoryUsedInMb;
    private String memoryStorage;
    private ThroughputMeasurement throughputMeasurement;
    private String publicEndpoint;
    private String privateEndpoint;
    private boolean activeActiveRedis;
    private RedisCloudDatabase[] crdbDatabases;
    private long readOperationsPerSecond;
    private long writeOperationsPerSecond;
    private String region;
}

