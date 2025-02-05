package com.redis.autoscaler.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PrometheusMetrics {
    private final Map<String, AtomicLong> configuredThroughput = new HashMap<>();
    private final MeterRegistry meterRegistry;


    public PrometheusMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void addConfiguredThroughput(String dbId, String instance, long throughput){
        if(configuredThroughput.containsKey(dbId)){
            configuredThroughput.get(dbId).set(throughput);
            return;
        }

        configuredThroughput.put(dbId, new AtomicLong(throughput));
        Gauge.builder("redis_db_configured_throughput", configuredThroughput.get(dbId), AtomicLong::get)
                .tag("db", dbId)
                .tag("instance", instance)
                .register(meterRegistry);
    }
}
