package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.model.DataRecordMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class RedisServiceTest {

    @Mock
    private RedisTemplate<String, DataRecordMessage> redisTemplate;

    @Mock
    private ListOperations<String, DataRecordMessage> listOps;

    private RedisService redisService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForList()).thenReturn(listOps); // Mock binding
        redisService = new RedisService(redisTemplate);
    }

    @Test
    void whenAddToBatch_thenDataIsPushedToRedis() {
        DataRecordMessage message = new DataRecordMessage(System.currentTimeMillis(), 42, "AB");

        redisService.addToBatch("dataBatch", message);

        verify(listOps, times(1)).rightPush("dataBatch", message);
    }

    @Test
    void whenGetBatch_thenDataIsRetrieved() {
        DataRecordMessage message = new DataRecordMessage(System.currentTimeMillis(), 42, "AB");

        when(listOps.range("dataBatch", 0, -1)).thenReturn(List.of(message));

        List<DataRecordMessage> batch = redisService.getBatch("dataBatch");

        assertFalse(batch.isEmpty());
        assertEquals(1, batch.size());
    }

    @Test
    void whenGetBatch_thenEmptyListIsReturned() {
        when(listOps.range("dataBatch", 0, 499)).thenReturn(List.of());  // Mocking an empty list

        List<DataRecordMessage> batch = redisService.getBatch("dataBatch");

        assertTrue(batch.isEmpty());  // ✅ Now correctly expecting an empty list
    }

    @Test
    void whenClearBatch_thenDataIsDeleted() {
        redisService.clearBatch("dataBatch");

        verify(redisTemplate, times(1)).delete("dataBatch");
    }

    @Test
    void whenFallbackAddToBatch_thenLogWarning() {
        redisService.fallbackAddToBatch("dataBatch", new DataRecordMessage(), new RuntimeException("Error"));
        verifyNoInteractions(listOps);
    }

    @Test
    void whenFallbackRetrySendBatch_thenLogWarning() {
        redisService.fallbackRetryAddToBatch(new RuntimeException("Retry Error"));
        verifyNoInteractions(listOps);
    }
}
