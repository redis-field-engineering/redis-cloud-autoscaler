package com.redis.autoscaler.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.*;
import com.redis.autoscaler.documents.AlertName;
import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.Task;
import com.redis.autoscaler.documents.TaskResponse;
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
            throw new RuntimeException("Failed to fetch database info");
        }

        return objectMapper.readValue(response.body(), RedisCloudDatabase.class);
    }

    public Optional<Task> applyRule(Rule rule, String dbId) throws IOException, InterruptedException {
        // Apply the rule to the database
        RedisCloudDatabase db = getDatabase(dbId);

        if(db == null){
            LOG.info("Database {} not found", dbId);
            return Optional.empty();
        }

        ScaleRequest scaleRequest;
        switch (rule.getRuleType()){
            case HighMemory -> {
                // we are at max configured scale, do nothing
                if(db.getDatasetSizeInGb() >= rule.getScaleCeiling()){
                    LOG.info("DB: {} ID: {} is above the memory scale ceiling: {}gb",db.getName(), dbId, rule.getScaleCeiling());
                    return Optional.empty();
                }
                double newDatasetSizeInGb = getNewDatasetSizeInGb(rule, db);
                scaleRequest = ScaleRequest.builder().datasetSizeInGb(newDatasetSizeInGb).build();
            }
            case LowMemory -> {
                // we are at min configured scale, do nothing
                if(db.getDatasetSizeInGb() <= rule.getScaleFloor()){
                    LOG.info("DB: {} ID: {} is below the memory scale floor: {}gb",db.getName(), dbId, rule.getScaleFloor());
                    return Optional.empty();
                }
                double newDatasetSizeInGb = getNewDatasetSizeInGb(rule, db);
                scaleRequest = ScaleRequest.builder().datasetSizeInGb(newDatasetSizeInGb).build();
            }
            case HighThroughput -> {
                // we are at max configured scale, do nothing
                if(db.getThroughputMeasurement().getValue() >= rule.getScaleCeiling()){
                    LOG.info("DB: {} ID: {} is above the throughput scale ceiling: {}ops/sec",db.getName(), dbId, rule.getScaleCeiling());
                    return Optional.empty();
                }
                long newThroughput = getNewThroughput(rule, db);

                scaleRequest = ScaleRequest.builder().throughputMeasurement(new ThroughputMeasurement(ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond, newThroughput)).build();
            }
            case LowThroughput -> {
                // we are at min configured scale, do nothing
                if(db.getThroughputMeasurement().getValue() <= rule.getScaleFloor()){
                    LOG.info("DB: {} ID: {} is below the throughput scale floor: {}ops/sec",db.getName(), dbId, rule.getScaleFloor());
                    return Optional.empty();
                }
                long newThroughput = getNewThroughput(rule, db);

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
        if(rule.getRuleType() == AlertName.HighThroughput){
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
        } else if(rule.getRuleType() == AlertName.LowThroughput){
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

        return newThroughput;
    }

    private static double getNewDatasetSizeInGb(Rule rule, RedisCloudDatabase db) {
        double newDatasetSizeInGb;
        if(rule.getRuleType() == AlertName.HighMemory){
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
        } else if(rule.getRuleType() == AlertName.LowMemory){
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
                throw new HttpResponseException(response.statusCode(), String.format("Failed to scale database %s", response.body()));
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
