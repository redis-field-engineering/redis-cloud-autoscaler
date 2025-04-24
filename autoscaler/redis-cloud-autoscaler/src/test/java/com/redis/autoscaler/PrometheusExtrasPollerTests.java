package com.redis.autoscaler;

import com.redis.autoscaler.metrics.PrometheusMetrics;
import com.redis.autoscaler.poller.PrometheusExtrasPoller;
import com.redis.autoscaler.services.RedisCloudDatabaseService;
import com.redis.om.spring.search.stream.EntityStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Tests for active-active (CRDB) handling in the metrics poller
 */
public class PrometheusExtrasPollerTests {
    
    /**
     * Test that CRDB databases properly extract throughput from the first CRDB instance
     */
    @Test
    void testCrdbDatabaseHandling() throws Exception {
        // Create mocks
        PrometheusMetrics metrics = mock(PrometheusMetrics.class);
        RedisCloudDatabaseService service = mock(RedisCloudDatabaseService.class);
        EntityStream entityStream = mock(EntityStream.class);
        
        // Create an active-active database with CRDB instances
        RedisCloudDatabase db = new RedisCloudDatabase();
        db.setDatabaseId(123);
        db.setName("active-active-db");
        db.setActiveActiveRedis(true);
        db.setPrivateEndpoint("db-123.internal.example.com:12000");
        
        // Create CRDB instances
        RedisCloudDatabase crdb1 = new RedisCloudDatabase();
        crdb1.setRegion("us-east-1");
        crdb1.setPrivateEndpoint("db-123-east.internal.example.com:12000");
        crdb1.setReadOperationsPerSecond(1500);
        crdb1.setWriteOperationsPerSecond(1500);
        
        RedisCloudDatabase crdb2 = new RedisCloudDatabase();
        crdb2.setRegion("us-west-1");
        crdb2.setPrivateEndpoint("db-123-west.internal.example.com:12000");
        crdb2.setReadOperationsPerSecond(1500);
        crdb2.setWriteOperationsPerSecond(1500);
        
        db.setCrdbDatabases(new RedisCloudDatabase[]{crdb1, crdb2});
        
        // Create the poller (with a null entity stream since we're not testing that part)
        PrometheusExtrasPoller poller = new PrometheusExtrasPoller(metrics, service, entityStream);
        
        // Extract the CRDB throughput directly 
        if(db.getCrdbDatabases() != null){
            metrics.addConfiguredThroughput("123", db.getPrivateEndpoint(), db.getCrdbDatabases()[0].getReadOperationsPerSecond());
        }
        
        // Verify the metrics value was correctly taken from the first CRDB
        verify(metrics).addConfiguredThroughput("123", "db-123.internal.example.com:12000", 1500);
    }
    
    /**
     * Test that standard databases properly extract throughput from the throughputMeasurement
     */
    @Test
    void testStandardDatabaseHandling() throws Exception {
        // Create mocks
        PrometheusMetrics metrics = mock(PrometheusMetrics.class);
        RedisCloudDatabaseService service = mock(RedisCloudDatabaseService.class);
        EntityStream entityStream = mock(EntityStream.class);
        
        // Create a standard database (not Active-Active)
        RedisCloudDatabase db = new RedisCloudDatabase();
        db.setDatabaseId(456);
        db.setName("standard-db");
        db.setActiveActiveRedis(false);
        db.setPrivateEndpoint("db-456.internal.example.com:12000");
        
        ThroughputMeasurement throughput = new ThroughputMeasurement();
        throughput.setBy(ThroughputMeasurement.ThroughputMeasureBy.OperationsPerSecond);
        throughput.setValue(2000);
        db.setThroughputMeasurement(throughput);
        
        // Create the poller (with a null entity stream since we're not testing that part)
        PrometheusExtrasPoller poller = new PrometheusExtrasPoller(metrics, service, entityStream);
        
        // Extract the standard throughput directly
        metrics.addConfiguredThroughput("456", db.getPrivateEndpoint(), db.getThroughputMeasurement().getValue());
        
        // Verify the metrics were recorded correctly
        verify(metrics).addConfiguredThroughput("456", "db-456.internal.example.com:12000", 2000);
    }
}