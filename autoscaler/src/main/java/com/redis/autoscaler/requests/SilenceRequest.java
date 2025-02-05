package com.redis.autoscaler.requests;

import com.redis.autoscaler.documents.AlertName;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SilenceRequest {
    private final List<SilenceMatcher> matchers;
    private final String startsAt;
    private final String endsAt;
    private final String createdBy = "autoscaler";
    private final String comment = "Silence created by Redis Autoscaler";


    public SilenceRequest(String instance, AlertName alertName, int durationMin){
        this.matchers = List.of(new SilenceMatcher(MatchType.Instance, instance), new SilenceMatcher(MatchType.AlertName, alertName.getValue()));
        this.startsAt = Instant.now().toString();
        this.endsAt = Instant.now().plusSeconds(durationMin * 60L).toString();
    }


}
