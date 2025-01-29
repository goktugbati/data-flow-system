package com.dataflow.datamongodbwriterservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rabbitmq")
@Getter
@Setter
public class RabbitMQProperties {
    private String queueName;
    private String exchangeName;
    private String routingKey;
    private int maxRetries;
}
