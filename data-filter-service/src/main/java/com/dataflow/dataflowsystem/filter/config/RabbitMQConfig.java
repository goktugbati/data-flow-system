package com.dataflow.dataflowsystem.filter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitMQConfig {
    private final RabbitMQProperties rabbitMQProperties;

    public RabbitMQConfig(RabbitMQProperties rabbitMQProperties) {
        this.rabbitMQProperties = rabbitMQProperties;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        Queue queue1 = new Queue(rabbitMQProperties.getDatabaseQueue(), true);
        Queue queue2 = new Queue(rabbitMQProperties.getMongodbQueue(), true);
        TopicExchange topicExchange = new TopicExchange(rabbitMQProperties.getExchangeName());
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        rabbitAdmin.declareExchange(topicExchange);
        rabbitAdmin.declareQueue(queue1);
        rabbitAdmin.declareQueue(queue2);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue1).to(topicExchange).with(rabbitMQProperties.getRoutingKey()));
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue2).to(topicExchange).with(rabbitMQProperties.getRoutingKey()));
        return rabbitAdmin;
    }

}