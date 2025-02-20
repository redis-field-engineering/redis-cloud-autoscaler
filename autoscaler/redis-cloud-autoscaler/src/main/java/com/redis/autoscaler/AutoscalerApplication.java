package com.redis.autoscaler;

import com.redis.autoscaler.documents.RuleRepository;
import com.redis.om.spring.annotations.EnableRedisDocumentRepositories;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;


@SpringBootApplication
@EnableRedisDocumentRepositories(basePackages = "com.redis.autoscaler.documents")
public class AutoscalerApplication {
	public static void main(String[] args) {
		SpringApplication.run(AutoscalerApplication.class, args);
	}

	@Autowired
	RuleRepository ruleRepository;


	@Bean
	AutoscalerConfig autoscalerConfig() {
		AutoscalerConfig config = new AutoscalerConfig();
		String subscriptionId = System.getenv("REDIS_CLOUD_SUBSCRIPTION_ID");
		config.setSubscriptionId(subscriptionId);
		return config;
	}

}
