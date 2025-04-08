package com.redis.autoscaler;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Region {
    private String region;
    private LocalThroughputMeasurement localThroughputMeasurement;
}