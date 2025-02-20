package com.redis.autoscaler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RedisCloudDatabase {

    private int databaseId;
    private String name;
//    private String protocol;
//    private String provider;
//    private String region;
//    private String redisVersion;
//    private String respVersion;
//    private String status;
    private double datasetSizeInGb;
    private int memoryUsedInMb;
    private String memoryStorage;
//    private boolean supportOSSClusterApi;
//    private boolean useExternalEndpointForOSSClusterApi;
//    private String dataPersistence;
//    private boolean replication;
//    private String dataEvictionPolicy;
    private ThroughputMeasurement throughputMeasurement;
//    private ZonedDateTime activatedOn;
//    private ZonedDateTime lastModified;
    private String publicEndpoint;
    private String privateEndpoint;
//    private Replica replica;
//    private Clustering clustering;
//    private Security security;
//    private List<Module> modules;
//    private List<Object> alerts;
//    private List<Link> links;

    // Getters and setters

//    @Data
//    public static class ThroughputMeasurement {
//        private String by;
//        private int value;
//
//        // Getters and setters
//    }

//    @Data
//    public static class Replica {
//        private List<SyncSource> syncSources;
//
//        // Getters and setters
//
//        @Data
//        public static class SyncSource {
//            private String endpoint;
//            private boolean encryption;
//            private String clientCert;
//
//            // Getters and setters
//        }
//    }

//    @Data
//    public static class Clustering {
//        private int numberOfShards;
//        private List<RegexRule> regexRules;
//        private String hashingPolicy;
//
//        // Getters and setters
//
//        @Data
//        public static class RegexRule {
//            private int ordinal;
//            private String pattern;
//
//            // Getters and setters
//        }
//    }
//
//    @Data
//    public static class Security {
//        private boolean enableDefaultUser;
//        private String password;
//        private boolean sslClientAuthentication;
//        private boolean tlsClientAuthentication;
//        private boolean enableTls;
//        private List<String> sourceIps;
//
//        // Getters and setters
//    }
//
//    @Data
//    public static class Module {
//        private int id;
//        private String name;
//        private String capabilityName;
//        private String version;
//        private String description;
//        private List<Object> parameters;
//
//        // Getters and setters
//    }
//
//    @Data
//    public static class Link {
//        private String rel;
//        private String href;
//        private String type;
//
//        // Getters and setters
//    }
}

