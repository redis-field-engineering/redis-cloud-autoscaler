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
import java.util.ArrayList;
import java.util.List;
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

    public int getDatabaseCount() throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/subscriptions/%s/databases", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId()));
        HttpRequest request = httpClientConfig.requestBuilder()
                .uri(uri)
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() != 200){
            throw new RuntimeException(String.format("Failed to fetch database count on %s", uri.toString()));
        }

        DatabaseListResponse databaseListResponse = objectMapper.readValue(response.body(), DatabaseListResponse.class);
        if(databaseListResponse.getSubscription().length == 0){
            LOG.warn("No subscriptions found for subscription id: {}", config.getSubscriptionId());
            return 0;
        }

        return databaseListResponse.getSubscription()[0].getDatabases().length;
    }

    public Optional<RedisCloudDatabase> getDatabaseByInternalInstanceName(String instanceName){
        try {
            URI uri = URI.create(String.format("%s/subscriptions/%s/databases", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId()));
            HttpRequest request = httpClientConfig.requestBuilder()
                    .uri(uri)
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() != 200){
                throw new RuntimeException(String.format("Failed to fetch database count on %s", uri.toString()));
            }

            DatabaseListResponse databaseListResponse = objectMapper.readValue(response.body(), DatabaseListResponse.class);
            if(databaseListResponse.getSubscription().length == 0){
                LOG.warn("No subscriptions found for subscription id: {}", config.getSubscriptionId());
                return Optional.empty();
            }

            for (RedisCloudDatabase db : databaseListResponse.getSubscription()[0].getDatabases()) {
                if(!db.isActiveActiveRedis()){
                    continue;
                }

                for(RedisCloudDatabase crdb : db.getCrdbDatabases()){
                    LOG.info("Checking crdb: {} against instance name: {}", crdb.getPrivateEndpoint(), instanceName);
                    String internalInstanceName = getInternalUriFromPrivateEndpoint(crdb.getPrivateEndpoint());
                    LOG.info("Internal instance name: {}", internalInstanceName);
                    String instanceNameHost = instanceName.split(":")[0];
                    if(internalInstanceName.equals(instanceNameHost)){
                        return Optional.of(db);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }


    public Optional<RedisCloudDatabase> getDatabase(String dbId) throws IOException, InterruptedException {

        URI uri = URI.create(String.format("%s/subscriptions/%s/databases/%s", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId(), dbId));

        HttpRequest request = httpClientConfig.requestBuilder()
                .uri(uri)
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() != 200){
            return Optional.empty();
        }

        return Optional.of(objectMapper.readValue(response.body(), RedisCloudDatabase.class));
    }

    public Optional<Task> applyRule(Rule rule) throws IOException, InterruptedException {
        String dbId = rule.getDbId();
        // Apply the rule to the database
        Optional<RedisCloudDatabase> dbOpt = getDatabase(dbId);

        if(dbOpt.isEmpty()){
            LOG.info("Database {} not found", dbId);
            return Optional.empty();
        }

        RedisCloudDatabase db = dbOpt.get();


        ScaleRequest scaleRequest;
        switch (rule.getRuleType()){
            case IncreaseMemory, DecreaseMemory -> {
                double currentDatasetSize;
                if(db.getCrdbDatabases() != null){
                    currentDatasetSize = db.getCrdbDatabases()[0].getDatasetSizeInGb();
                } else {
                    currentDatasetSize = db.getDatasetSizeInGb();
                }

                // we are at min configured scale, do nothing
                if(rule.getRuleType() == RuleType.DecreaseMemory && currentDatasetSize <= rule.getScaleFloor()){
                    LOG.info("DB: {} ID: {} is already below the memory scale floor: {}gb",db.getName(), dbId, rule.getScaleFloor());
                    return Optional.empty();
                } else if(rule.getRuleType() == RuleType.IncreaseMemory && currentDatasetSize >= rule.getScaleCeiling()){
                    LOG.info("DB: {} ID: {} is already above the memory scale ceiling: {}gb",db.getName(), dbId, rule.getScaleCeiling());
                    return Optional.empty();
                }


                double newDatasetSizeInGb = getNewDatasetSizeInGb(rule, currentDatasetSize);
                if(newDatasetSizeInGb == db.getDatasetSizeInGb()){
                    LOG.info("DB: {} ID: {} is already at the min/max memory: {}gb",db.getName(), dbId, newDatasetSizeInGb);
                    return Optional.empty();
                }

                newDatasetSizeInGb = roundUpToNearestTenth(newDatasetSizeInGb); // round up to nearest 0.1gb for Redis Cloud

                scaleRequest = ScaleRequest.builder().datasetSizeInGb(newDatasetSizeInGb).isCrdb(db.isActiveActiveRedis()).build();
            }
            case IncreaseThroughput, DecreaseThroughput -> {
                long currentThroughput;
                if(db.isActiveActiveRedis()){
                    currentThroughput = db.getCrdbDatabases()[0].getReadOperationsPerSecond();
                } else{
                    currentThroughput = db.getThroughputMeasurement().getValue();
                }

                if(db.getThroughputMeasurement() != null && db.getThroughputMeasurement().getBy() != ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond){
                    LOG.info("DB: {} ID: {} is not measured by ops/sec, cannot apply ops/sec rule: {}",db.getName(), dbId, rule.getRuleType());
                    return Optional.empty();
                }

                long newThroughput = getNewThroughput(rule, currentThroughput);
                if(newThroughput == currentThroughput){
                    LOG.info("DB: {} ID: {} is already at the min/max ops/sec: {}",db.getName(), dbId, newThroughput);
                    return Optional.empty();
                }

                if(db.isActiveActiveRedis()){
                    List<Region> regions = new ArrayList<>();
                    for(RedisCloudDatabase crdb : db.getCrdbDatabases()){
                        Region region = Region.builder()
                                .region(crdb.getRegion())
                                .localThroughputMeasurement(
                                        LocalThroughputMeasurement.builder()
                                                .readOperationsPerSecond(newThroughput)
                                                .writeOperationsPerSecond(newThroughput)
                                                .build())
                                .build();

                        regions.add(region);
                    }

                    scaleRequest = ScaleRequest.builder()
                            .regions(regions.toArray(new Region[0]))
                            .isCrdb(true)
                            .build();
                }
                else{
                    scaleRequest = ScaleRequest.builder().isCrdb(false).throughputMeasurement(new ThroughputMeasurement(ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond, newThroughput)).build();
                }
            }
            default -> {
                return Optional.empty();
            }
        }

        return Optional.of(scaleDatabase(dbId, scaleRequest));
    }

    private static long getNewThroughput(Rule rule, long currentThroughput){
        long newThroughput;
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
                    newThroughput = (long)Math.ceil(currentThroughput * rule.getScaleValue());
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

    private static double getNewDatasetSizeInGb(Rule rule, double currentDataSetSize) {
        double newDatasetSizeInGb;
        if(rule.getRuleType() == RuleType.IncreaseMemory){
            switch (rule.getScaleType()){
                case Step -> {
                    newDatasetSizeInGb = currentDataSetSize + rule.getScaleValue();
                }
                case Exponential -> {
                    newDatasetSizeInGb = currentDataSetSize * rule.getScaleValue();
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
                    newDatasetSizeInGb = currentDataSetSize - rule.getScaleValue();
                }
                case Exponential -> {
                    newDatasetSizeInGb = currentDataSetSize * rule.getScaleValue();
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
        URI uri;

        if(request.isCrdb()){
            uri = URI.create(String.format("%s/subscriptions/%s/databases/%s/regions", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId(), dbId));

        }else{
            uri = URI.create(String.format("%s/subscriptions/%s/databases/%s", Constants.REDIS_CLOUD_URI_BASE, config.getSubscriptionId(), dbId));
        }

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

    private static String getInternalUriFromPrivateEndpoint(String privateEndpoint){
        String splitPort = privateEndpoint.split(":")[0];
        int firstDotIndex = splitPort.indexOf(".");
        return splitPort.substring(firstDotIndex+1);
    }
}
