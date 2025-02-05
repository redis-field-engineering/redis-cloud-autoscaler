package com.redis.autoscaler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

@Configuration
public class HttpClientConfig {
    @Bean
    public HttpClient redisCloudApiClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public HttpRequest.Builder requestBuilder() {
        String redisCloudAccountSecret = System.getenv("REDIS_CLOUD_ACCOUNT_KEY");
        String redisCloudApiKey = System.getenv("REDIS_CLOUD_API_KEY");

        return HttpRequest.newBuilder()
                .header("x-api-key", redisCloudAccountSecret)
                .header("x-api-secret-key", redisCloudApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    public HttpRequest.Builder silencerRequestBuilder(){
        String alertManagerHost = System.getenv("ALERT_MANAGER_HOST");
        int alertManagerPort = Integer.parseInt(System.getenv("ALERT_MANAGER_PORT"));
        return HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://%s:%d/api/v2/silences", alertManagerHost, alertManagerPort)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

    }
}
