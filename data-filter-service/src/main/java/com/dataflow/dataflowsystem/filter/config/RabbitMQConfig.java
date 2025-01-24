package com.dataflow.dataflowsystem.filter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class RabbitMQConfig {

    private static final String EXCHANGE_NAME = "data-exchange";
    private static final String DATABASE_QUEUE = "database-queue";
    private static final String MONGODB_QUEUE = "mongodb-queue";
    private static final String ROUTING_KEY = "common.event";

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        Queue queue1 = new Queue(DATABASE_QUEUE, true);
        Queue queue2 = new Queue(MONGODB_QUEUE, true);
        TopicExchange topicExchange = new TopicExchange(EXCHANGE_NAME);
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        rabbitAdmin.declareExchange(new TopicExchange(EXCHANGE_NAME));
        rabbitAdmin.declareQueue(queue1);
        rabbitAdmin.declareQueue(queue2);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue1).to(topicExchange).with(ROUTING_KEY));
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue2).to(topicExchange).with(ROUTING_KEY));
        return rabbitAdmin;
    }

    @Bean
    public MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of("com.dataflow.model.DataRecord"));
        return converter;
    }


}