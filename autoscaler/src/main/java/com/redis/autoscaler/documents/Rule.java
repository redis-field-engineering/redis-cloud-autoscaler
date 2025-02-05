package com.redis.autoscaler.documents;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.redis.om.spring.annotations.Document;
import com.redis.om.spring.annotations.Indexed;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Document
@Data
//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "ruleType", visible = true)
//@JsonSubTypes(
//        {
//                @JsonSubTypes.Type(value = HighMemoryRule.class, name = "HighMemory"),
//                @JsonSubTypes.Type(value = LowMemoryRule.class, name = "LowMemory")
//        }
//)
public class Rule {
    @Indexed
    protected String dbId;

    @Indexed
    @Id
    protected String ruleId;

    @Indexed
    protected AlertName ruleType;

    @Indexed
    protected ScaleType scaleType;

    protected double scaleValue;
    protected double scaleCeiling;
    protected double scaleFloor;

    @Override
    public String toString(){
        return String.format("Rule: dbId=%s, ruleId=%s, ruleType=%s, scaleType=%s, scaleValue=%f, scaleCeiling=%f, scaleFloor=%f",
                dbId, ruleId, ruleType, scaleType, scaleValue, scaleCeiling, scaleFloor);
    }


    public boolean isValid(){
        if(this.ruleType == AlertName.HighMemory){
            if(scaleType == ScaleType.Deterministic || scaleType == ScaleType.Step){
                if(scaleValue > scaleCeiling){
                    return false;
                }

            }

            if(scaleType == ScaleType.Deterministic) {
                if(!isMultipleOfPointOne()){
                    return false;
                }
            }
        }

        if(this.ruleType == AlertName.LowMemory){
            if(scaleType == ScaleType.Deterministic){
                if(scaleValue < scaleFloor){
                    return false;
                }
            }

            if(scaleType == ScaleType.Deterministic) {
                if(!isMultipleOfPointOne()){
                    return false;
                }
            }
        }

        return true;
    }

    protected boolean isMultipleOfPointOne() {
        double compValue = scaleValue - Math.floor(scaleValue);
        double epsilon = 1e-9; // Small tolerance for floating-point precision
        return Math.abs(compValue % 0.1) < epsilon;
    }

//    public boolean isRuleValid(){
//        if(ruleType == AlertName.HighMemory){
//            if(scaleType == ScaleType.Deterministic || scaleType == ScaleType.Step){
//                if(scaleValue > scaleCeiling){
//                    return false;
//                }
//            }
//        }
//
//        if (ruleType == AlertName.LowMemory){
//            if(scaleType == ScaleType.Deterministic){
//                if(scaleValue < scaleFloor){
//                    return false;
//                }
//            }
//        }
//
//        return true;
//
//    }
}
