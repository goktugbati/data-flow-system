package com.dataflow.dataflowsystem.filter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());  // ✅ Force JSON serialization
        return template;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(CachingConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);

        Queue dbQueue = createQueueWithDLQ(rabbitMQProperties.getDatabaseQueue());
        Queue mongoQueue = createQueueWithDLQ(rabbitMQProperties.getMongodbQueue());

        Queue dbDlq = new Queue(rabbitMQProperties.getDatabaseQueue() + ".dlq", true);
        Queue mongoDlq = new Queue(rabbitMQProperties.getMongodbQueue() + ".dlq", true);

        TopicExchange topicExchange = new TopicExchange(rabbitMQProperties.getExchangeName());

        rabbitAdmin.declareExchange(topicExchange);
        rabbitAdmin.declareQueue(dbQueue);
        rabbitAdmin.declareQueue(mongoQueue);
        rabbitAdmin.declareQueue(dbDlq);
        rabbitAdmin.declareQueue(mongoDlq);

        rabbitAdmin.declareBinding(BindingBuilder.bind(dbQueue).to(topicExchange).with(rabbitMQProperties.getRoutingKey()));
        rabbitAdmin.declareBinding(BindingBuilder.bind(mongoQueue).to(topicExchange).with(rabbitMQProperties.getRoutingKey()));

        rabbitAdmin.declareBinding(BindingBuilder.bind(dbDlq).to(topicExchange).with(rabbitMQProperties.getRoutingKey() + ".dlq"));
        rabbitAdmin.declareBinding(BindingBuilder.bind(mongoDlq).to(topicExchange).with(rabbitMQProperties.getRoutingKey() + ".dlq"));

        return rabbitAdmin;
    }

    private Queue createQueueWithDLQ(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", rabbitMQProperties.getExchangeName())
                .withArgument("x-dead-letter-routing-key", queueName + ".dlq")
                .build();
    }
}
