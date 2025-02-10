package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataGeneratorServiceTest {

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private RedisService redisService;

    private DataGeneratorService dataGeneratorService;

    @BeforeEach
    void setup() {
        dataGeneratorService = new DataGeneratorService(webSocketHandler, redisService);
    }

    @Test
    void whenGenerateData_thenDataIsStoredInRedis() {
        dataGeneratorService.generateData();

        verify(redisService, times(1)).addToBatch(eq("dataBatch"), any(DataRecordMessage.class));
    }

    @Test
    void whenSendBatch_thenBatchIsSentSuccessfully() throws Exception {
        DataRecordMessage message = new DataRecordMessage(System.currentTimeMillis(), 42, "AB");
        when(redisService.getBatch("dataBatch")).thenReturn(List.of(message));

        dataGeneratorService.sendBatch();

        verify(webSocketHandler, times(1)).sendBatch(anyList());
        verify(redisService, times(1)).clearBatch("dataBatch");
    }

    @Test
    void whenSendBatchFails_thenRetryMechanismTriggers() throws Exception {
        DataRecordMessage message = new DataRecordMessage(System.currentTimeMillis(), 42, "AB");
        when(redisService.getBatch("dataBatch")).thenReturn(List.of(message));
        doThrow(new RuntimeException("WebSocket error")).when(webSocketHandler).sendBatch(anyList());

        dataGeneratorService.sendBatch();

        verify(webSocketHandler, atLeastOnce()).sendBatch(anyList());
        verify(redisService, never()).clearBatch("dataBatch");
    }

    @Test
    void whenBatchIsEmpty_thenNoDataIsSent() throws Exception {
        when(redisService.getBatch("dataBatch")).thenReturn(List.of());

        dataGeneratorService.sendBatch();

        verify(webSocketHandler, never()).sendBatch(anyList());
        verify(redisService, never()).clearBatch("dataBatch");
    }

    @Test
    void whenGenerateHashValue_thenConsistentOutput() {
        Long timestamp = 1234567890L;
        Integer value = 42;

        String hash1 = dataGeneratorService.generateHashValue(timestamp, value);
        String hash2 = dataGeneratorService.generateHashValue(timestamp, value);

        assertEquals(hash1, hash2);
        assertEquals(2, hash1.length());
    }

    @Test
    void whenCircuitBreakerOpens_thenFallbackIsTriggered() {
        dataGeneratorService.fallbackGenerateData(new RuntimeException("Circuit breaker open"));
        verifyNoInteractions(redisService, webSocketHandler);
    }

    @Test
    void whenRetryFails_thenFallbackIsTriggered() {
        dataGeneratorService.fallbackRetrySendBatch(new RuntimeException("Retry exhausted"));
        verifyNoInteractions(redisService, webSocketHandler);
    }
}