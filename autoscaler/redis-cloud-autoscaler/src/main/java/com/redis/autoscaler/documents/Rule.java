package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redis.om.spring.annotations.Document;
import com.redis.om.spring.annotations.Indexed;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.springframework.data.annotation.Id;

@Document
@Data
public class Rule {
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Rule.class);

    @Indexed
    @Id
    private String ruleId;

    @JsonProperty(required = true)
    @Indexed
    private String dbId;

    @Indexed
    @JsonProperty(required = true)
    @NotNull(message = "ruleType is required")
    private RuleType ruleType;

    @Indexed
    @JsonProperty(required = true)
    @NotNull(message = "scaleType is required")
    private ScaleType scaleType;

    @Indexed
    @JsonProperty(required = true)
    @NotNull(message = "triggerType is required")
    private TriggerType triggerType;

    private String triggerValue;

    @JsonProperty(required = true)
    @NotNull(message = "scaleValue is required")
    private double scaleValue;

    private double scaleCeiling;
    private double scaleFloor;

    @Override
    public String toString(){
        return String.format("Rule: dbId=%s, ruleId=%s, ruleType=%s, scaleType=%s, scaleValue=%f, scaleCeiling=%f, scaleFloor=%f, triggerType=%s, triggerValue=%s",
                dbId, ruleId, ruleType, scaleType, scaleValue, scaleCeiling, scaleFloor, triggerType, triggerValue);
    }


    public String getValidationError(){
        LOG.info("Validating rule: {}", this);

        if(this.ruleType == RuleType.IncreaseMemory){
            if(scaleType == ScaleType.Deterministic || scaleType == ScaleType.Step){
                if(scaleValue > scaleCeiling){
                    return "Scale value is greater than scale ceiling";
                }

            }

            if(scaleType == ScaleType.Deterministic) {
                if(!isMultipleOfPointOne()){
                    return "Memory Scale must be a multiple of 0.1";
                }
            }
        }

        if(this.ruleType == RuleType.DecreaseMemory){
            if(scaleType == ScaleType.Deterministic){
                if(scaleValue < scaleFloor){
                    return "Scale value is less than scale floor";
                }
            } else {
                return "Non-deterministic Scale in Operations are not supported"; // non-deterministic decrease rules are not allowed
            }


        }

        if(this.ruleType == RuleType.IncreaseThroughput){
            if(scaleType == ScaleType.Deterministic || scaleType == ScaleType.Step){
                if(scaleValue > scaleCeiling){
                    return "Scale value is greater than scale ceiling";
                }
            }
        }

        if(this.ruleType == RuleType.DecreaseThroughput){
            if(scaleType == ScaleType.Deterministic ){
                if(scaleValue < scaleFloor){
                    return "Scale value is less than scale floor";
                }
            } else {
                LOG.info("Non-deterministic decrease rules are not allowed: rule type: {}", ruleType);
                return "Non-deterministic Scale in Operations are not supported"; // non-deterministic decrease rules are not allowed
            }
        }

        return "";
    }

    private boolean isMultipleOfPointOne() {
        LOG.info("Validating scale value: {}", scaleValue);
        double compValue = scaleValue - Math.floor(scaleValue);
        double epsilon = 1e-9; // Small tolerance for floating-point precision
        return Math.abs(compValue % 0.1) < epsilon;
    }
}
