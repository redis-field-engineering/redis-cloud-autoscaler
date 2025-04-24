package com.redis.autoscaler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.documents.Rule;
import com.redis.autoscaler.documents.RuleType;
import com.redis.autoscaler.documents.ScaleType;
import com.redis.autoscaler.documents.Task;
import com.redis.autoscaler.documents.TaskResponse;
import com.redis.autoscaler.documents.TaskStatus;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RedisCloudDatabaseServiceTests {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpClientConfig httpClientConfig;

    @Mock
    private AutoscalerConfig config;

    @Mock
    private HttpResponse<Object> httpResponse;

    private RedisCloudDatabaseService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        when(config.getSubscriptionId()).thenReturn("subscription-123");
        when(httpClientConfig.requestBuilder()).thenReturn(HttpRequest.newBuilder());

        service = new RedisCloudDatabaseService(httpClient, httpClientConfig, config);
    }

    private RedisCloudDatabase createActiveDatabaseWithCRDB() {
        // Main database
        RedisCloudDatabase db = new RedisCloudDatabase();
        db.setDatabaseId(123);
        db.setName("my-active-active-db");
        db.setActiveActiveRedis(true);
        db.setDatasetSizeInGb(1.0);
        
        // CRDB instances (one per region)
        RedisCloudDatabase crdb1 = new RedisCloudDatabase();
        crdb1.setRegion("us-east-1");
        crdb1.setPrivateEndpoint("db-123.internal.example.com:12000");
        crdb1.setReadOperationsPerSecond(1000);
        crdb1.setWriteOperationsPerSecond(1000);
        crdb1.setDatasetSizeInGb(1.0);
        
        RedisCloudDatabase crdb2 = new RedisCloudDatabase();
        crdb2.setRegion("us-west-1");
        crdb2.setPrivateEndpoint("db-456.internal.example.com:12000");
        crdb2.setReadOperationsPerSecond(1000);
        crdb2.setWriteOperationsPerSecond(1000);
        crdb2.setDatasetSizeInGb(1.0);
        
        db.setCrdbDatabases(new RedisCloudDatabase[]{crdb1, crdb2});
        return db;
    }

    @Test
    void testGetInternalUriFromPrivateEndpoint() throws Exception {
        // This is testing the private method through its usage in public methods
        String instanceName = "internal.example.com";
        
        // Create test database
        RedisCloudDatabase db = createActiveDatabaseWithCRDB();
        
        // Mock database retrieval response
        Subscription sub = new Subscription();
        sub.setDatabases(new RedisCloudDatabase[]{db});
        DatabaseListResponse response = new DatabaseListResponse();
        response.setSubscription(new Subscription[]{sub});
        
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(response));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);

        // Test the getDatabaseByInternalInstanceName method which uses the private helper
        Optional<RedisCloudDatabase> result = service.getDatabaseByInternalInstanceName(instanceName + ":12000");
        
        assertTrue(result.isPresent());
        assertEquals(db.getDatabaseId(), result.get().getDatabaseId());
    }

    @Test
    void testScaleDatabaseCRDBMemory() throws Exception {
        // Create a database with CRDB instances
        RedisCloudDatabase db = createActiveDatabaseWithCRDB();
        
        // Mock database retrieval
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(db));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);
        
        // Create rule for memory scaling
        Rule rule = new Rule();
        rule.setRuleType(RuleType.IncreaseMemory);
        rule.setScaleType(ScaleType.Step);
        rule.setScaleValue(0.5);
        rule.setScaleCeiling(5.0);
        rule.setScaleFloor(0.5);
        rule.setDbId("123");
        
        // Create mock task response
        TaskResponse taskResponse = new TaskResponse();
        taskResponse.setTaskId("task-123");
        taskResponse.setStatus(TaskStatus.processingInProgress);
        
        // For the scale operation
        HttpResponse<Object> scaleResponse = mock(HttpResponse.class);
        when(scaleResponse.statusCode()).thenReturn(202);
        when(scaleResponse.body()).thenReturn(objectMapper.writeValueAsString(taskResponse));
        
        // For the database count check
        DatabaseListResponse dbListResponse = new DatabaseListResponse();
        Subscription sub = new Subscription();
        sub.setDatabases(new RedisCloudDatabase[]{db});
        dbListResponse.setSubscription(new Subscription[]{sub});
        
        HttpResponse<Object> countResponse = mock(HttpResponse.class);
        when(countResponse.statusCode()).thenReturn(200);
        when(countResponse.body()).thenReturn(objectMapper.writeValueAsString(dbListResponse));
        
        // Set up the sequence of responses
        when(httpClient.send(any(), any()))
                .thenReturn(httpResponse)  // First call - get database
                .thenReturn(countResponse) // Second call - get database count
                .thenReturn(scaleResponse); // Third call - scale database
        
        // Execute the rule
        Optional<Task> taskOpt = service.applyRule(rule);
        
        // Verify results
        assertTrue(taskOpt.isPresent());
        Task task = taskOpt.get();
        assertEquals("task-123", task.getTaskId());
        assertEquals("123", task.getDbId());
        
        // Verify the scale request was correct
        ScaleRequest scaleRequest = task.getScaleRequest();
        assertTrue(scaleRequest.isCrdb());
        assertEquals(1.5, scaleRequest.getDatasetSizeInGb(), 0.01); // Initial 1.0 + 0.5 step
    }

    @Test
    void testScaleDatabaseCRDBThroughput() throws Exception {
        // Create a database with CRDB instances
        RedisCloudDatabase db = createActiveDatabaseWithCRDB();
        
        // Mock database retrieval
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(db));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);
        
        // Create rule for throughput scaling
        Rule rule = new Rule();
        rule.setRuleType(RuleType.IncreaseThroughput);
        rule.setScaleType(ScaleType.Step);
        rule.setScaleValue(500);
        rule.setScaleCeiling(5000);
        rule.setScaleFloor(500);
        rule.setDbId("123");
        
        // Create mock task response
        TaskResponse taskResponse = new TaskResponse();
        taskResponse.setTaskId("task-123");
        taskResponse.setStatus(TaskStatus.processingInProgress);
        
        // For the scale operation
        HttpResponse<Object> scaleResponse = mock(HttpResponse.class);
        when(scaleResponse.statusCode()).thenReturn(202);
        when(scaleResponse.body()).thenReturn(objectMapper.writeValueAsString(taskResponse));
        
        // For the database count check
        DatabaseListResponse dbListResponse = new DatabaseListResponse();
        Subscription sub = new Subscription();
        sub.setDatabases(new RedisCloudDatabase[]{db});
        dbListResponse.setSubscription(new Subscription[]{sub});
        
        HttpResponse<Object> countResponse = mock(HttpResponse.class);
        when(countResponse.statusCode()).thenReturn(200);
        when(countResponse.body()).thenReturn(objectMapper.writeValueAsString(dbListResponse));
        
        // Set up the sequence of responses
        when(httpClient.send(any(), any()))
                .thenReturn(httpResponse)  // First call - get database
                .thenReturn(countResponse) // Second call - get database count
                .thenReturn(scaleResponse); // Third call - scale database
        
        // Execute the rule
        Optional<Task> taskOpt = service.applyRule(rule);
        
        // Verify results
        assertTrue(taskOpt.isPresent());
        Task task = taskOpt.get();
        assertEquals("task-123", task.getTaskId());
        assertEquals("123", task.getDbId());
        
        // Verify the scale request was correct
        ScaleRequest scaleRequest = task.getScaleRequest();
        assertTrue(scaleRequest.isCrdb());
        assertNotNull(scaleRequest.getRegions());
        assertEquals(2, scaleRequest.getRegions().length);
        
        // Check that each region has the correct throughput settings
        for (Region region : scaleRequest.getRegions()) {
            assertEquals(1500, region.getLocalThroughputMeasurement().getReadOperationsPerSecond()); // Initial 1000 + 500 step
            assertEquals(1500, region.getLocalThroughputMeasurement().getWriteOperationsPerSecond()); // Initial 1000 + 500 step
        }
    }

    @Test
    void testRoundUpToNearestTenth() throws Exception {
        // This is testing the method through its effect on scaling
        RedisCloudDatabase db = createActiveDatabaseWithCRDB();
        
        // Mock database retrieval
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(db));
        when(httpClient.send(any(), any())).thenReturn(httpResponse);
        
        // Create rule that would result in non-rounded memory size
        Rule rule = new Rule();
        rule.setRuleType(RuleType.IncreaseMemory);
        rule.setScaleType(ScaleType.Step);
        rule.setScaleValue(0.45); // This should result in 1.45GB which should be rounded to 1.5GB
        rule.setScaleCeiling(5.0);
        rule.setScaleFloor(0.5);
        rule.setDbId("123");
        
        // Create mock task response
        TaskResponse taskResponse = new TaskResponse();
        taskResponse.setTaskId("task-123");
        taskResponse.setStatus(TaskStatus.processingInProgress);
        
        // For the scale operation
        HttpResponse<Object> scaleResponse = mock(HttpResponse.class);
        when(scaleResponse.statusCode()).thenReturn(202);
        when(scaleResponse.body()).thenReturn(objectMapper.writeValueAsString(taskResponse));
        
        // For the database count check
        DatabaseListResponse dbListResponse = new DatabaseListResponse();
        Subscription sub = new Subscription();
        sub.setDatabases(new RedisCloudDatabase[]{db});
        dbListResponse.setSubscription(new Subscription[]{sub});
        
        HttpResponse<Object> countResponse = mock(HttpResponse.class);
        when(countResponse.statusCode()).thenReturn(200);
        when(countResponse.body()).thenReturn(objectMapper.writeValueAsString(dbListResponse));
        
        // Set up the sequence of responses
        when(httpClient.send(any(), any()))
                .thenReturn(httpResponse)  // First call - get database
                .thenReturn(countResponse) // Second call - get database count
                .thenReturn(scaleResponse); // Third call - scale database
        
        // Execute the rule
        Optional<Task> taskOpt = service.applyRule(rule);
        
        // Verify results
        assertTrue(taskOpt.isPresent());
        Task task = taskOpt.get();
        assertEquals("task-123", task.getTaskId());
        
        // Verify the memory size was rounded up to nearest 0.1GB
        ScaleRequest scaleRequest = task.getScaleRequest();
        assertEquals(1.5, scaleRequest.getDatasetSizeInGb(), 0.001); // 1.0 + 0.45 = 1.45, rounded up to 1.5
    }
    
    @Test
    void testRoundUpToNearest500() {
        // Test this through its effect on throughput scaling
        RedisCloudDatabase db = createActiveDatabaseWithCRDB();
        
        try {
            // Mock database retrieval
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(objectMapper.writeValueAsString(db));
            
            // Create rule that would result in non-rounded throughput
            Rule rule = new Rule();
            rule.setRuleType(RuleType.IncreaseThroughput);
            rule.setScaleType(ScaleType.Step);
            rule.setScaleValue(480); // This should result in 1480 ops which should be rounded to 1500 ops
            rule.setScaleCeiling(5000);
            rule.setScaleFloor(500);
            rule.setDbId("123");
            
            // Create mock task response
            TaskResponse taskResponse = new TaskResponse();
            taskResponse.setTaskId("task-123");
            taskResponse.setStatus(TaskStatus.processingInProgress);
            
            // For the scale operation
            HttpResponse<Object> scaleResponse = mock(HttpResponse.class);
            when(scaleResponse.statusCode()).thenReturn(202);
            when(scaleResponse.body()).thenReturn(objectMapper.writeValueAsString(taskResponse));
            
            // For the database count check
            DatabaseListResponse dbListResponse = new DatabaseListResponse();
            Subscription sub = new Subscription();
            sub.setDatabases(new RedisCloudDatabase[]{db});
            dbListResponse.setSubscription(new Subscription[]{sub});
            
            HttpResponse<Object> countResponse = mock(HttpResponse.class);
            when(countResponse.statusCode()).thenReturn(200);
            when(countResponse.body()).thenReturn(objectMapper.writeValueAsString(dbListResponse));
            
            // Set up the sequence of responses
            when(httpClient.send(any(), any()))
                    .thenReturn(httpResponse)  // First call - get database
                    .thenReturn(countResponse) // Second call - get database count
                    .thenReturn(scaleResponse); // Third call - scale database
            
            // Execute the rule
            Optional<Task> taskOpt = service.applyRule(rule);
            
            // Verify results
            assertTrue(taskOpt.isPresent());
            Task task = taskOpt.get();
            
            // Verify the throughput was rounded up to nearest 500 ops
            ScaleRequest scaleRequest = task.getScaleRequest();
            Region[] regions = scaleRequest.getRegions();
            for (Region region : regions) {
                assertEquals(1500, region.getLocalThroughputMeasurement().getReadOperationsPerSecond()); 
                assertEquals(1500, region.getLocalThroughputMeasurement().getWriteOperationsPerSecond());
            }
        } catch (IOException | InterruptedException e) {
            fail("Exception should not be thrown: " + e.getMessage());
        }
    }
}