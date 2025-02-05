package com.redis.autoscaler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.documents.AlertName;
import com.redis.autoscaler.documents.AlertWebhook;
import com.redis.autoscaler.documents.Rule;
import com.redis.om.spring.client.RedisModulesClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@SpringBootTest
class AutoscalerApplicationTests {
	@MockitoBean
	private JedisConnectionFactory jedisConnectionFactory;
	@MockitoBean
	private RedisModulesClient redisModulesClient;



	private static final ObjectMapper objectMapper = createObjectMapper();

	private static ObjectMapper createObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		return objectMapper;
	}

	@Test
	void webhookDeserializes() throws IOException {
		// read resources/samplePayload.json into string
		ClassLoader classLoader = getClass().getClassLoader();

		String filePath = Objects.requireNonNull(classLoader.getResource("webhooks/HighMemory.json")).getPath();
		String jsonContent = Files.readString(Path.of(filePath));

		Assert.notNull(jsonContent, "jsonContent is null");

		AlertWebhook webhook = objectMapper.readValue(jsonContent, AlertWebhook.class);

		Assert.notNull(webhook, "webhook is null");
		Assert.isTrue(webhook.getCommonLabels().getAlertName() == AlertName.HighMemory, "commonLabels is not HighMemory");
	}

	@Test
	@SneakyThrows
	void testRuleDeserialization(){
		ClassLoader classLoader = getClass().getClassLoader();
		String filePath = Objects.requireNonNull(classLoader.getResource("rules/DeterministicMemoryUpRule.json")).getPath();
		String jsonContent = Files.readString(Path.of(filePath));
		Rule rule = objectMapper.readValue(jsonContent, Rule.class);
		Assert.isTrue(rule.getRuleType() == AlertName.HighMemory, "ruleType is not HighMemory");
//		Assert.isTrue(rule instanceof HighMemoryRule, "rule is not HighMemoryRule");
	}
}
