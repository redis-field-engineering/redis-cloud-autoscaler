package com.redis.autoscaler;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalThroughputMeasurement {
    private String region;
    private long readOperationsPerSecond;
    private long writeOperationsPerSecond;

}
