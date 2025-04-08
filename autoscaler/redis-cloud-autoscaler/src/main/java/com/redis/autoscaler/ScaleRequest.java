package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleRequest {
    @Builder.Default
    @JsonIgnore
    private boolean isCrdb = false;
    private Double datasetSizeInGb;
    private ThroughputMeasurement throughputMeasurement;
    private Region[] regions;

    @Override
    public String toString(){
        return String.format("ScaleRequest: datasetSizeInGb=%f, throughputMeasurement=%s",
                datasetSizeInGb, throughputMeasurement);
    }
}