package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
//@Builder
public class ThroughputMeasurement {

    private ThroughputMeasureBy by;
    private long value;

    public ThroughputMeasurement(){
    }

    public enum ThroughputMeasureBy{


        OperationsPerSecond("operations-per-second"),
        NumberOfShards("number-of-shards");

        private final String value;

        ThroughputMeasureBy(String value){
            this.value = value;
        }

        @JsonValue
        public String getValue(){
            return value;
        }

        @Override
        public String toString(){
            return value;
        }
    }
}
