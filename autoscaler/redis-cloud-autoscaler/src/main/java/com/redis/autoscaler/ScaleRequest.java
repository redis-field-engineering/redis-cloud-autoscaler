package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Data
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
