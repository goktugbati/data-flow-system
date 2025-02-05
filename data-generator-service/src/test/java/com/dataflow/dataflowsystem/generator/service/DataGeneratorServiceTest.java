package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.config.WebSocketProperties;
import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataGeneratorServiceTest {

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private WebSocketProperties webSocketProperties;

    @Mock
    private FilterServiceHealthCheckService filterServiceHealthCheckService;

    private DataGeneratorService dataGeneratorService;

    @BeforeEach
    void setup() {
        dataGeneratorService = new DataGeneratorService(
                webSocketHandler,
                webSocketProperties,
                filterServiceHealthCheckService);
    }

    @Test
    void whenGenerateData_thenDataRecordHasCorrectFormat() throws JsonProcessingException {
        when(filterServiceHealthCheckService.isFilterServiceAvailable()).thenReturn(true);
        when(webSocketProperties.getMaxRetryAttempts()).thenReturn(3);
        ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);
        doAnswer(invocation -> {
            return null;
        }).when(webSocketHandler).sendMessage(any(DataRecordMessage.class));

        dataGeneratorService.generateAndSendData();

        verify(webSocketHandler).sendMessage(recordCaptor.capture());

        DataRecordMessage capturedRecord = recordCaptor.getValue();
        assertAll(
                "Data Record Validation",
                () -> assertNotNull(capturedRecord, "Record should not be null"),
                () -> assertNotNull(capturedRecord.getTimestamp(), "Timestamp should not be null"),
                () -> assertTrue(capturedRecord.getRandomValue() >= 0 &&
                        capturedRecord.getRandomValue() <= 100, "Random value should be between 0 and 100"),
                () -> assertNotNull(capturedRecord.getHashValue(), "Hash value should not be null"),
                () -> assertEquals(2, capturedRecord.getHashValue().length(),
                        "Hash value should be exactly 2 characters")
        );
    }


    @Test
    void whenWebSocketHandlerThrowsException_thenRetryAndHandleGracefully() throws JsonProcessingException {
        // Given
        when(filterServiceHealthCheckService.isFilterServiceAvailable()).thenReturn(true);
        when(webSocketProperties.getMaxRetryAttempts()).thenReturn(3);
        doThrow(new RuntimeException("WebSocket error"))
                .when(webSocketHandler)
                .sendMessage(any(DataRecordMessage.class));

        dataGeneratorService.generateAndSendData();

        verify(webSocketHandler, times(3)).sendMessage(any(DataRecordMessage.class));
    }

    @Test
    void whenFilterServiceDown_thenNoDataGenerated() throws JsonProcessingException {
        when(filterServiceHealthCheckService.isFilterServiceAvailable()).thenReturn(false);

        dataGeneratorService.generateAndSendData();

        verify(webSocketHandler, never()).sendMessage(any(DataRecordMessage.class));
    }

    @Test
    void testHashValueConsistency() {
        Long timestamp = 1234567890L;
        Integer value = 42;

        String hash1 = dataGeneratorService.generateHashValue(timestamp, value);
        String hash2 = dataGeneratorService.generateHashValue(timestamp, value);

        assertEquals(hash1, hash2, "Hash values should be consistent for same input");
        assertEquals(2, hash1.length(), "Hash value should be exactly 2 characters");
    }
}