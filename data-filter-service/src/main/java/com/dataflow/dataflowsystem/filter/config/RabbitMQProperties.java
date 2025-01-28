package com.dataflow.dataflowsystem.filter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rabbitmq")
@Getter
@Setter
public class RabbitMQProperties {
    private String host;
    private int port;
    private String exchangeName;
    private String databaseQueue;
    private String mongodbQueue;
    private String routingKey;
    private int maxRetries;
}
