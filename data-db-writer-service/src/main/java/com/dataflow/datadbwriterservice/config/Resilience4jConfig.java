package com.dataflow.datadbwriterservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .slidingWindowSize(2)
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerConfig circuitBreakerConfig) {
        return CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    @Bean
    public CircuitBreakerConfigCustomizer redisCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer
                .of("redisService",
                        builder -> builder
                                .waitDurationInOpenState(Duration.ofSeconds(10))
                                .slidingWindowSize(5)
                                .failureRateThreshold(40.0f)
                                .recordException(throwable ->
                                        throwable instanceof RedisConnectionFailureException)
                );
    }
}