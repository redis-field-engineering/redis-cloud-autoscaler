package com.redis.autoscaler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redis.autoscaler.documents.RuleRepository;
import com.redis.autoscaler.documents.RuleType;
import com.redis.autoscaler.documents.AlertWebhook;
import com.redis.autoscaler.documents.Rule;
import com.redis.om.spring.client.RedisModulesClient;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@SpringBootTest
class AutoscalerApplicationTests {
	@MockBean
	private JedisConnectionFactory jedisConnectionFactory;
	@MockBean
	private RedisModulesClient redisModulesClient;
	@MockBean
	private RuleRepository ruleRepository;



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

		String filePath = Objects.requireNonNull(classLoader.getResource("webhooks/IncreaseMemory.json")).getPath();
		String jsonContent = Files.readString(Path.of(filePath));

		Assert.notNull(jsonContent, "jsonContent is null");

		AlertWebhook webhook = objectMapper.readValue(jsonContent, AlertWebhook.class);

		Assert.notNull(webhook, "webhook is null");
		Assert.isTrue(webhook.getCommonLabels().getRuleType() == RuleType.IncreaseMemory, "commonLabels is not IncreaseMemory");
	}

	@Test
	@SneakyThrows
	void testRuleDeserialization(){
		ClassLoader classLoader = getClass().getClassLoader();
		String filePath = Objects.requireNonNull(classLoader.getResource("rules/DeterministicMemoryUpRule.json")).getPath();
		String jsonContent = Files.readString(Path.of(filePath));
		Rule rule = objectMapper.readValue(jsonContent, Rule.class);
		Assert.isTrue(rule.getRuleType() == RuleType.IncreaseMemory, "ruleType is not IncreaseMemory");
	}
}
