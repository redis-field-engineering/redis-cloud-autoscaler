package com.redis.autoscaler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrdbModelTests {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void testRegionSerialization() throws Exception {
        // Create a LocalThroughputMeasurement object
        LocalThroughputMeasurement throughput = LocalThroughputMeasurement.builder()
                .readOperationsPerSecond(1500)
                .writeOperationsPerSecond(1500)
                .build();
        
        // Create a Region object
        Region region = Region.builder()
                .region("us-east-1")
                .localThroughputMeasurement(throughput)
                .build();
        
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(region);
        
        // Verify JSON structure
        assertTrue(json.contains("\"region\":\"us-east-1\""));
        assertTrue(json.contains("\"localThroughputMeasurement\""));
        assertTrue(json.contains("\"readOperationsPerSecond\":1500"));
        assertTrue(json.contains("\"writeOperationsPerSecond\":1500"));
        
        // Deserialize from JSON
        Region deserializedRegion = objectMapper.readValue(json, Region.class);
        
        // Verify deserialized object
        assertEquals("us-east-1", deserializedRegion.getRegion());
        assertNotNull(deserializedRegion.getLocalThroughputMeasurement());
        assertEquals(1500, deserializedRegion.getLocalThroughputMeasurement().getReadOperationsPerSecond());
        assertEquals(1500, deserializedRegion.getLocalThroughputMeasurement().getWriteOperationsPerSecond());
    }
    
    @Test
    void testLocalThroughputMeasurementSerialization() throws Exception {
        // Create a LocalThroughputMeasurement object
        LocalThroughputMeasurement throughput = LocalThroughputMeasurement.builder()
                .region("us-west-1")
                .readOperationsPerSecond(2000)
                .writeOperationsPerSecond(1000)
                .build();
        
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(throughput);
        
        // Verify JSON structure
        assertTrue(json.contains("\"region\":\"us-west-1\""));
        assertTrue(json.contains("\"readOperationsPerSecond\":2000"));
        assertTrue(json.contains("\"writeOperationsPerSecond\":1000"));
        
        // Deserialize from JSON
        LocalThroughputMeasurement deserializedThroughput = objectMapper.readValue(json, LocalThroughputMeasurement.class);
        
        // Verify deserialized object
        assertEquals("us-west-1", deserializedThroughput.getRegion());
        assertEquals(2000, deserializedThroughput.getReadOperationsPerSecond());
        assertEquals(1000, deserializedThroughput.getWriteOperationsPerSecond());
    }
    
    @Test
    void testScaleRequestWithRegions() throws Exception {
        // Create LocalThroughputMeasurement objects
        LocalThroughputMeasurement throughput1 = LocalThroughputMeasurement.builder()
                .readOperationsPerSecond(1500)
                .writeOperationsPerSecond(1500)
                .build();
        
        LocalThroughputMeasurement throughput2 = LocalThroughputMeasurement.builder()
                .readOperationsPerSecond(1500)
                .writeOperationsPerSecond(1500)
                .build();
        
        // Create Region objects
        Region region1 = Region.builder()
                .region("us-east-1")
                .localThroughputMeasurement(throughput1)
                .build();
        
        Region region2 = Region.builder()
                .region("us-west-1")
                .localThroughputMeasurement(throughput2)
                .build();
        
        // Create ScaleRequest with regions
        ScaleRequest request = ScaleRequest.builder()
                .isCrdb(true)
                .regions(new Region[]{region1, region2})
                .build();
        
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(request);
        
        // Verify JSON structure (isCrdb should not be included as it's marked @JsonIgnore)
        assertFalse(json.contains("\"isCrdb\""));
        assertTrue(json.contains("\"regions\""));
        assertTrue(json.contains("\"region\":\"us-east-1\""));
        assertTrue(json.contains("\"region\":\"us-west-1\""));
        
        // Deserialize from JSON
        ScaleRequest deserializedRequest = objectMapper.readValue(json, ScaleRequest.class);
        
        // Verify deserialized object
        assertFalse(deserializedRequest.isCrdb()); // Default value is false
        assertNotNull(deserializedRequest.getRegions());
        assertEquals(2, deserializedRequest.getRegions().length);
        assertEquals("us-east-1", deserializedRequest.getRegions()[0].getRegion());
        assertEquals("us-west-1", deserializedRequest.getRegions()[1].getRegion());
    }
    
    @Test
    void testRedisCloudDatabaseWithCRDB() {
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
        
        // Create main database
        RedisCloudDatabase db = new RedisCloudDatabase();
        db.setDatabaseId(123);
        db.setName("active-active-db");
        db.setActiveActiveRedis(true);
        db.setCrdbDatabases(new RedisCloudDatabase[]{crdb1, crdb2});
        
        // Verify properties
        assertTrue(db.isActiveActiveRedis());
        assertNotNull(db.getCrdbDatabases());
        assertEquals(2, db.getCrdbDatabases().length);
        assertEquals("us-east-1", db.getCrdbDatabases()[0].getRegion());
        assertEquals("us-west-1", db.getCrdbDatabases()[1].getRegion());
        assertEquals(1500, db.getCrdbDatabases()[0].getReadOperationsPerSecond());
        assertEquals(1500, db.getCrdbDatabases()[0].getWriteOperationsPerSecond());
    }
}