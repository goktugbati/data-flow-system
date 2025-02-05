package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.config.WebSocketProperties;
import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataGeneratorServiceTest {
//
//    @Mock
//    private WebSocketHandler webSocketHandler;
//    @Mock
//    private WebSocketProperties webSocketProperties;
//    @Mock
//    private RestTemplate restTemplate;
//
//    private DataGeneratorService dataGeneratorService;
//
//    @BeforeEach
//    void setup() {
//        lenient().when(webSocketProperties.getMaxRetryAttempts()).thenReturn(3);
//        dataGeneratorService = new DataGeneratorService(webSocketHandler, webSocketProperties);
//    }
//
//    @Test
//    void whenGenerateData_thenDataRecordHasCorrectFormat() throws JsonProcessingException {
//        ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);
//        dataGeneratorService.generateAndSendData();
//        verify(webSocketHandler).sendMessage(recordCaptor.capture());
//        DataRecordMessage capturedRecord = recordCaptor.getValue();
//
//        assertNotNull(capturedRecord);
//        assertNotNull(capturedRecord.getTimestamp());
//        assertTrue(capturedRecord.getRandomValue() >= 0 && capturedRecord.getRandomValue() <= 100);
//        assertNotNull(capturedRecord.getHashValue());
//        assertEquals(2, capturedRecord.getHashValue().length());
//    }
//
//    @Test
//    void whenWebSocketHandlerThrowsException_thenHandleGracefully() throws JsonProcessingException {
//        doThrow(new RuntimeException("WebSocket error"))
//                .when(webSocketHandler)
//                .sendMessage(any(DataRecordMessage.class));
//        assertDoesNotThrow(() -> dataGeneratorService.generateAndSendData());
//    }
//
//    @Test
//    void testFilterServiceCircuitBreaker() {
//        when(restTemplate.getForEntity(anyString(), eq(String.class)))
//                .thenThrow(new RuntimeException("Service unavailable"));
//        boolean available = dataGeneratorService.isFilterServiceAvailable();
//        assertFalse(available);
//    }
//
//    @Test
//    void testRetryLogic() throws JsonProcessingException {
//        doThrow(new RuntimeException("WebSocket error"))
//                .when(webSocketHandler)
//                .sendMessage(any(DataRecordMessage.class));
//        dataGeneratorService.generateAndSendData();
//        verify(webSocketHandler, times(3)).sendMessage(any(DataRecordMessage.class));
//    }
//
//    @Test
//    void testRandomValueDistribution() throws JsonProcessingException {
//        Set<Integer> generatedValues = new HashSet<>();
//        int iterations = 1000;
//        for (int i = 0; i < iterations; i++) {
//            dataGeneratorService.generateAndSendData();
//            ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);
//            verify(webSocketHandler, times(i + 1)).sendMessage(recordCaptor.capture());
//            generatedValues.add(recordCaptor.getValue().getRandomValue());
//        }
//        assertTrue(generatedValues.size() > 50);
//    }
}
