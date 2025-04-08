package com.redis.autoscaler;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Region {
    private String region;
    private LocalThroughputMeasurement localThroughputMeasurement;
}
