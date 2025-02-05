package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Data
public class ScaleRequest {
    private double datasetSizeInGb;
    private ThroughputMeasurement throughputMeasurement;

    @Override
    public String toString(){
        return String.format("ScaleRequest: datasetSizeInGb=%f, throughputMeasurement=%s",
                datasetSizeInGb, throughputMeasurement);
    }
}
