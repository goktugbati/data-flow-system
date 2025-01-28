package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.config.RabbitMQProperties;
import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageQueueService {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;

    public MessageQueueService(RabbitTemplate rabbitTemplate, RabbitMQProperties rabbitMQProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQProperties = rabbitMQProperties;
    }

    public void send(DataRecordMessage record) {
        int retries = 0;
        while (retries < rabbitMQProperties.getMaxRetries()) {
            try {
                rabbitTemplate.convertAndSend(rabbitMQProperties.getExchangeName(), rabbitMQProperties.getRoutingKey(), record);
                log.info("Sent to queue exchange-key {} and routing-key {} : {}",
                        rabbitMQProperties.getExchangeName(),
                        rabbitMQProperties.getRoutingKey(),
                        record);
                return;
            } catch (Exception e) {
                retries++;
                log.error("Error sending message (Attempt {}/{}): {}", retries, rabbitMQProperties.getMaxRetries(), e.getMessage());
                if (retries == rabbitMQProperties.getMaxRetries()) {
                    log.error("Sending to DLQ: {}", record);
                    rabbitTemplate.convertAndSend(rabbitMQProperties.getExchangeName(), rabbitMQProperties.getRoutingKey() + ".dlq", record);
                }
            }
        }
    }
}