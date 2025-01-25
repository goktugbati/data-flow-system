package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.model.DataRecordMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageQueueService {
    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE_NAME = "data-exchange";
    private static final String ROUTING_KEY = "common.event";

    public MessageQueueService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(DataRecordMessage record) {
        try {
            log.info("Attempting to send message to exchange: {} with routing key: {}",
                    EXCHANGE_NAME, ROUTING_KEY);
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, record);
            log.info("Successfully sent message to queue: {}", record);
        } catch (Exception e) {
            log.error("Error sending message to queue: {}", e.getMessage(), e);
        }
    }
}