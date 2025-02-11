package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.model.DataRecordMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RedisService {

    private final RedisTemplate<String, DataRecordMessage> redisTemplate;
    private final ListOperations<String, DataRecordMessage> listOps;
    @Value("${batch.buffer-size:500}")
    private int bufferSize;

    public RedisService(RedisTemplate<String, DataRecordMessage> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.listOps = redisTemplate.opsForList();
    }

    @CircuitBreaker(name = "redisCircuitBreaker", fallbackMethod = "fallbackAddToBatch")
    @Retry(name = "redisRetry", fallbackMethod = "fallbackRetryAddToBatch")
    public void addToBatch(String key, DataRecordMessage message) {
        listOps.rightPush(key, message);
    }

    public void fallbackAddToBatch(String key, DataRecordMessage message, Throwable t) {
        log.warn("Redis write failed for key {}: {}", key, t.getMessage());
    }

    public void fallbackRetryAddToBatch(Throwable t) {
        log.warn("Retry failed for WebSocket batch sending: {}", t.getMessage());
    }


    public List<DataRecordMessage> getBatch(String key) {
        return listOps.range(key, 0, bufferSize - 1);
    }

    public void clearBatch(String key) {
        redisTemplate.delete(key);
    }
}
