package com.redis.autoscaler.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.Constants;
import com.redis.autoscaler.HttpClientConfig;
import com.redis.autoscaler.documents.AlertName;
import com.redis.autoscaler.requests.SilenceRequest;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class SilencerService {
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SilencerService.class);
    private final HttpClient httpClient;
    private final HttpClientConfig httpClientConfig;
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public SilencerService(HttpClient httpClient, HttpClientConfig httpClientConfig) {
        this.httpClient = httpClient;
        this.httpClientConfig = httpClientConfig;
    }

    @SneakyThrows
    public void silenceAlert(String instance, AlertName alertName, int durationMin){
         SilenceRequest silenceRequest = new SilenceRequest(instance, alertName, durationMin);
         String json = objectMapper.writeValueAsString(silenceRequest);
         LOG.info("Silencing alert: " + json);
         HttpRequest request = httpClientConfig.silencerRequestBuilder()
                 .POST(HttpRequest.BodyPublishers.ofString(json))
                 .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() != 200){
            LOG.error("Failed to silence alert: " + response.body());
        }

        LOG.info("Alert silenced successfully " + response.body());
    }
}
