package com.redis.autoscaler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Active-Active CRDB (conflict-free replicated database) functionality
 * These tests validate the basic data models and functionality introduced to support
 * Active-Active databases in Redis Cloud
 */
public class CrdbFunctionalityTests {
    
    @Test
    void testScaleRequestWithCRDBFlag() {
        ScaleRequest request = ScaleRequest.builder()
                .isCrdb(true)
                .datasetSizeInGb(1.5)
                .build();
        
        assertTrue(request.isCrdb());
        assertEquals(1.5, request.getDatasetSizeInGb(), 0.001);
    }
    
    @Test
    void testScaleRequestWithRegions() {
        // Create regions
        LocalThroughputMeasurement throughput1 = LocalThroughputMeasurement.builder()
                .readOperationsPerSecond(1500)
                .writeOperationsPerSecond(1500)
                .build();
        
        Region region1 = Region.builder()
                .region("us-east-1")
                .localThroughputMeasurement(throughput1)
                .build();
        
        // Create request with regions
        ScaleRequest request = ScaleRequest.builder()
                .isCrdb(true)
                .regions(new Region[]{region1})
                .build();
        
        assertTrue(request.isCrdb());
        assertNotNull(request.getRegions());
        assertEquals(1, request.getRegions().length);
        assertEquals("us-east-1", request.getRegions()[0].getRegion());
        assertEquals(1500, request.getRegions()[0].getLocalThroughputMeasurement().getReadOperationsPerSecond());
    }
    
    @Test
    void testActiveActiveFlagInDatabase() {
        // Test setting and getting activeActiveRedis flag
        RedisCloudDatabase db = new RedisCloudDatabase();
        assertFalse(db.isActiveActiveRedis()); // Default should be false
        
        db.setActiveActiveRedis(true);
        assertTrue(db.isActiveActiveRedis());
    }
    
    @Test
    void testCrdbDatabasesInMainDatabase() {
        // Create main database
        RedisCloudDatabase db = new RedisCloudDatabase();
        db.setDatabaseId(123);
        db.setName("active-active-db");
        db.setActiveActiveRedis(true);
        
        // Create CRDB instances
        RedisCloudDatabase crdb1 = new RedisCloudDatabase();
        crdb1.setRegion("us-east-1");
        crdb1.setPrivateEndpoint("db-123-east.internal.example.com:12000");
        crdb1.setReadOperationsPerSecond(1500);
        
        RedisCloudDatabase crdb2 = new RedisCloudDatabase();
        crdb2.setRegion("us-west-1");
        crdb2.setPrivateEndpoint("db-123-west.internal.example.com:12000");
        crdb2.setReadOperationsPerSecond(1500);
        
        // Set CRDBs in main database
        db.setCrdbDatabases(new RedisCloudDatabase[]{crdb1, crdb2});
        
        // Verify
        assertNotNull(db.getCrdbDatabases());
        assertEquals(2, db.getCrdbDatabases().length);
        assertEquals("us-east-1", db.getCrdbDatabases()[0].getRegion());
        assertEquals("us-west-1", db.getCrdbDatabases()[1].getRegion());
        assertEquals(1500, db.getCrdbDatabases()[0].getReadOperationsPerSecond());
    }
    
    @Test
    void testLocalThroughputMeasurement() {
        LocalThroughputMeasurement measurement = LocalThroughputMeasurement.builder()
                .region("us-east-1")
                .readOperationsPerSecond(2000)
                .writeOperationsPerSecond(1000)
                .build();
        
        assertEquals("us-east-1", measurement.getRegion());
        assertEquals(2000, measurement.getReadOperationsPerSecond());
        assertEquals(1000, measurement.getWriteOperationsPerSecond());
    }
    
    @Test
    void testRegionBuilder() {
        LocalThroughputMeasurement throughput = LocalThroughputMeasurement.builder()
                .readOperationsPerSecond(2000)
                .writeOperationsPerSecond(1000)
                .build();
        
        Region region = Region.builder()
                .region("us-west-1")
                .localThroughputMeasurement(throughput)
                .build();
        
        assertEquals("us-west-1", region.getRegion());
        assertNotNull(region.getLocalThroughputMeasurement());
        assertEquals(2000, region.getLocalThroughputMeasurement().getReadOperationsPerSecond());
        assertEquals(1000, region.getLocalThroughputMeasurement().getWriteOperationsPerSecond());
    }
}