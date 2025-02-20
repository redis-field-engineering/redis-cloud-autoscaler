package com.redis.autoscaler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        String redisHostAndPort = System.getenv("REDIS_HOST_AND_PORT");
		String redisHost = redisHostAndPort.split(":")[0];
		int redisPort = Integer.parseInt(redisHostAndPort.split(":")[1]);
		String redisPassword = System.getenv("REDIS_PASSWORD");
		String redisUser = System.getenv("REDIS_USER") != null ? System.getenv("REDIS_USER") : "default";
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);

        if(redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        if (redisUser != null && !redisUser.isEmpty()) {
            config.setUsername(redisUser);
        }

        return new JedisConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String,Object> redisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        RedisTemplate<String,Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(jedisConnectionFactory);
        return redisTemplate;
    }
}
