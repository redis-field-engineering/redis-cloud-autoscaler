package com.redis.autoscaler.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.*;
import com.redis.autoscaler.documents.*;
import org.apache.hc.client5.http.HttpResponseException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;

@Service
public class RedisCloudDatabaseService {
    private final static Logger LOG = org.slf4j.LoggerFactory.getLogger(RedisCloudDatabaseService.class);
    private final HttpClient httpClient;
    private final HttpClientConfig httpClientConfig;
    private final AutoscalerConfig config;
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public RedisCloudDatabaseService(HttpClient httpClient, HttpClientConfig httpRequestBuilder, AutoscalerConfig config) {
        this.httpClient = httpClient;
        this.httpClientConfig = httpRequestBuilder;
        this.config = config;
    }

    public RedisCloudDatabase getDatabase(String dbId) throws IOException, InterruptedException {

        URI uri = URI.create(String.format("%s/subscriptions/%s/databases/%s", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId(), dbId));

        HttpRequest request = httpClientConfig.requestBuilder()
                .uri(uri)
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() != 200){
            throw new RuntimeException(String.format("Failed to fetch database info on %s", uri.toString()));
        }

        return objectMapper.readValue(response.body(), RedisCloudDatabase.class);
    }

    public Optional<Task> applyRule(Rule rule) throws IOException, InterruptedException {
        String dbId = rule.getDbId();
        // Apply the rule to the database
        RedisCloudDatabase db = getDatabase(dbId);

        if(db == null){
            LOG.info("Database {} not found", dbId);
            return Optional.empty();
        }

        ScaleRequest scaleRequest;
        switch (rule.getRuleType()){
            case IncreaseMemory, DecreaseMemory -> {
                // we are at min configured scale, do nothing
                if(db.getDatasetSizeInGb() <= rule.getScaleFloor()){
                    LOG.info("DB: {} ID: {} is below the memory scale floor: {}gb",db.getName(), dbId, rule.getScaleFloor());
                    return Optional.empty();
                }
                double newDatasetSizeInGb = getNewDatasetSizeInGb(rule, db);
                if(newDatasetSizeInGb == db.getDatasetSizeInGb()){
                    LOG.info("DB: {} ID: {} is already at the min/max memory: {}gb",db.getName(), dbId, newDatasetSizeInGb);
                    return Optional.empty();
                }

                scaleRequest = ScaleRequest.builder().datasetSizeInGb(newDatasetSizeInGb).build();
            }
            case IncreaseThroughput, DecreaseThroughput -> {
                if(db.getThroughputMeasurement().getBy() != ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond && rule.getScaleType() != ScaleType.Deterministic){
                    LOG.info("DB: {} ID: {} is not measured by ops/sec, cannot apply ops/sec rule: {}",db.getName(), dbId, rule.getRuleType());
                    return Optional.empty();
                }

                long newThroughput = getNewThroughput(rule, db);
                if(newThroughput == db.getThroughputMeasurement().getValue()){
                    LOG.info("DB: {} ID: {} is already at the min/max ops/sec: {}",db.getName(), dbId, newThroughput);
                    return Optional.empty();
                }

                scaleRequest = ScaleRequest.builder().throughputMeasurement(new ThroughputMeasurement(ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond, newThroughput)).build();
            }
            default -> {
                return Optional.empty();
            }
        }

        return Optional.of(scaleDatabase(dbId, scaleRequest));
    }

    private static long getNewThroughput(Rule rule, RedisCloudDatabase db){
        long newThroughput;
        long currentThroughput = db.getThroughputMeasurement().getValue();
        if(rule.getRuleType() == RuleType.IncreaseThroughput){
            switch (rule.getScaleType()){
                case Step -> {
                    newThroughput = currentThroughput + (long)rule.getScaleValue();
                }
                case Exponential -> {
                    newThroughput = (long)Math.ceil(currentThroughput * rule.getScaleValue());
                }
                case Deterministic -> {
                    newThroughput = (long)rule.getScaleValue();
                }

                default -> throw new IllegalStateException("Unexpected value: " + rule.getScaleType());
            }

            newThroughput = Math.min(newThroughput, (long)rule.getScaleCeiling());
        } else if(rule.getRuleType() == RuleType.DecreaseThroughput){
            switch (rule.getScaleType()){
                case Step -> {
                    newThroughput = currentThroughput - (long)rule.getScaleValue();
                }
                case Exponential -> {
                    newThroughput = (long)Math.ceil(db.getThroughputMeasurement().getValue() * rule.getScaleValue());
                }
                case Deterministic -> {
                    newThroughput = (long)rule.getScaleValue();
                }

                default -> throw new IllegalStateException("Unexpected value: " + rule.getScaleType());
            }

            newThroughput = (long)Math.max(newThroughput, rule.getScaleFloor());
        } else {
            throw new IllegalStateException("Unexpected value: " + rule.getRuleType());
        }

        return  roundUpToNearest500(newThroughput);
    }

    public static long roundUpToNearest500(long value){
        return (long) (Math.ceil(value / 500.0) * 500);
    }

    private static double getNewDatasetSizeInGb(Rule rule, RedisCloudDatabase db) {
        double newDatasetSizeInGb;
        if(rule.getRuleType() == RuleType.IncreaseMemory){
            switch (rule.getScaleType()){
                case Step -> {
                    newDatasetSizeInGb = db.getDatasetSizeInGb() + rule.getScaleValue();
                }
                case Exponential -> {
                    newDatasetSizeInGb = db.getDatasetSizeInGb() * rule.getScaleValue();
                }
                case Deterministic -> {
                    newDatasetSizeInGb = rule.getScaleValue();
                }

                default -> throw new IllegalStateException("Unexpected value: " + rule.getScaleType());
            }

            newDatasetSizeInGb = Math.min(newDatasetSizeInGb, rule.getScaleCeiling());
        } else if(rule.getRuleType() == RuleType.DecreaseMemory){
            switch (rule.getScaleType()){
                case Step -> {
                    newDatasetSizeInGb = roundUpToNearestTenth(db.getDatasetSizeInGb() - rule.getScaleValue());
                }
                case Exponential -> {
                    newDatasetSizeInGb = roundUpToNearestTenth(db.getDatasetSizeInGb() * rule.getScaleValue());
                }
                case Deterministic -> {
                    newDatasetSizeInGb = rule.getScaleValue();
                }

                default -> throw new IllegalStateException("Unexpected value: " + rule.getScaleType());
            }

            newDatasetSizeInGb = Math.max(newDatasetSizeInGb, rule.getScaleFloor());
        } else {
            throw new IllegalStateException("Unexpected value: " + rule.getRuleType());
        }

        return newDatasetSizeInGb;
    }

    Task scaleDatabase(String dbId, ScaleRequest request){
        URI uri = URI.create(String.format("%s/subscriptions/%s/databases/%s", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId(), dbId));
        LOG.info("Scaling database {} with request: {}", dbId, request);
        try {
            HttpRequest httpRequest = httpClientConfig.requestBuilder()
                    .uri(uri)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == 202){
                TaskResponse taskResponse = objectMapper.readValue(response.body(), TaskResponse.class);
                Task task = taskResponse.toTask();
                task.setScaleRequest(request);
                task.setDbId(dbId);
                Instant now = Instant.now();
                task.setLastObservedTimestamp(now);
                task.setInitialTimeStamp(now);
                return task;
            }

            else {
                throw new HttpResponseException(response.statusCode(), String.format("Failed to scale database %s %s", dbId, response.body()));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static double roundUpToNearestTenth(double value){
        return Math.ceil(value * 10) / 10;
    }

    private static double roundDownToNearestTenth(double value){
        return Math.floor(value * 10) / 10;
    }
}
