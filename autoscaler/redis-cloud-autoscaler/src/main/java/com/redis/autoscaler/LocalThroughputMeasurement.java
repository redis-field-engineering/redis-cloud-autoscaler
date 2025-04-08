package com.redis.autoscaler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalThroughputMeasurement {
    private String region;
    private long readOperationsPerSecond;
    private long writeOperationsPerSecond;
}