package com.dataflow.datadbwriterservice.service;

import com.dataflow.datadbwriterservice.exception.MessageBrokerException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageBrokerService {

    public void acknowledgeMessage(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicAck(deliveryTag, false);
                log.debug("Message acknowledged: {}", deliveryTag);
            } else {
                log.warn("Channel is closed, cannot acknowledge message: {}", deliveryTag);
            }
        } catch (Exception e) {
            log.error("Failed to acknowledge message: {}", deliveryTag, e);
            throw new MessageBrokerException("Failed to acknowledge message", e);
        }
    }

    public void rejectMessage(Channel channel, long deliveryTag, boolean requeue) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, requeue);
                log.debug("Message rejected (requeue={}): {}", requeue, deliveryTag);
            } else {
                log.warn("Channel is closed, cannot reject message: {}", deliveryTag);
            }
        } catch (Exception e) {
            log.error("Failed to reject message: {}", deliveryTag, e);
            throw new MessageBrokerException("Failed to reject message", e);
        }
    }
}
