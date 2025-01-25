package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataGeneratorServiceTest {

    @Mock
    private WebSocketHandler webSocketHandler;

    @InjectMocks
    private DataGeneratorService dataGeneratorService;

    @Test
    void whenGenerateData_thenDataRecordHasCorrectFormat() throws Exception {
        ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);

        dataGeneratorService.generateAndSendData();

        verify(webSocketHandler).sendMessage(recordCaptor.capture());
        DataRecordMessage capturedRecord = recordCaptor.getValue();

        assertNotNull(capturedRecord);
        assertNotNull(capturedRecord.getTimestamp());
        assertTrue(capturedRecord.getRandomValue() >= 0 && capturedRecord.getRandomValue() <= 100);
        assertNotNull(capturedRecord.getHashValue());
        assertEquals(2, capturedRecord.getHashValue().length());
    }

    @Test
    void whenWebSocketHandlerThrowsException_thenHandleGracefully() throws Exception {
        doThrow(new RuntimeException("WebSocket error"))
                .when(webSocketHandler)
                .sendMessage(any(DataRecordMessage.class));

        assertDoesNotThrow(() -> dataGeneratorService.generateAndSendData());
    }

    @Test
    void verifyHashConsistency() {
        Long timestamp = System.currentTimeMillis();
        Integer value = 50;

        String hash1 = dataGeneratorService.generateHashValue(timestamp, value);
        String hash2 = dataGeneratorService.generateHashValue(timestamp, value);

        assertEquals(hash1, hash2, "Same input should produce same hash");
        assertEquals(2, hash1.length(), "Hash should be exactly 2 characters");
        assertTrue(hash1.matches("[0-9a-f]{2}"), "Hash should be hexadecimal");
    }

    @Test
    void testHashWithEdgeCases() {
        String hashZero = dataGeneratorService.generateHashValue(0L, 0);
        assertNotNull(hashZero);
        assertEquals(2, hashZero.length());
        assertTrue(hashZero.matches("[0-9a-f]{2}"));

        String hashMax = dataGeneratorService.generateHashValue(Long.MAX_VALUE, 100);
        assertNotNull(hashMax);
        assertEquals(2, hashMax.length());
        assertTrue(hashMax.matches("[0-9a-f]{2}"));

        String hash1 = dataGeneratorService.generateHashValue(System.currentTimeMillis(), 0);
        String hash2 = dataGeneratorService.generateHashValue(System.currentTimeMillis(), 100);
        assertTrue(hash1.matches("[0-9a-f]{2}"));
        assertTrue(hash2.matches("[0-9a-f]{2}"));
    }

    @Test
    void testRandomValueDistribution() throws JsonProcessingException {
        Set<Integer> generatedValues = new HashSet<>();
        int iterations = 1000;

        // Generate multiple values to check distribution
        for (int i = 0; i < iterations; i++) {
            ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);
            dataGeneratorService.generateAndSendData();
            verify(webSocketHandler, times(i + 1)).sendMessage(recordCaptor.capture());
            generatedValues.add(recordCaptor.getValue().getRandomValue());
        }

        assertTrue(generatedValues.size() > 50, "Should generate diverse values");
        assertFalse(generatedValues.stream().anyMatch(v -> v < 0 || v > 100),
                "All values should be within range");
    }

    @Test
    void testTimestampIsRecent() throws Exception {
        ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);

        long beforeGeneration = System.currentTimeMillis();
        dataGeneratorService.generateAndSendData();
        long afterGeneration = System.currentTimeMillis();

        verify(webSocketHandler).sendMessage(recordCaptor.capture());
        DataRecordMessage record = recordCaptor.getValue();

        assertTrue(record.getTimestamp() >= beforeGeneration);
        assertTrue(record.getTimestamp() <= afterGeneration);
    }

    @Test
    void testMultipleGenerations() throws Exception {
        int generations = 5;
        List<DataRecordMessage> records = new ArrayList<>();

        for (int i = 0; i < generations; i++) {
            ArgumentCaptor<DataRecordMessage> recordCaptor = ArgumentCaptor.forClass(DataRecordMessage.class);
            dataGeneratorService.generateAndSendData();
            verify(webSocketHandler, times(i + 1)).sendMessage(recordCaptor.capture());
            records.add(recordCaptor.getValue());
            Thread.sleep(10);
        }

        Set<Long> timestamps = records.stream()
                .map(DataRecordMessage::getTimestamp)
                .collect(Collectors.toSet());
        assertEquals(generations, timestamps.size(), "Each generation should have unique timestamp");

        Set<String> hashes = records.stream()
                .map(DataRecordMessage::getHashValue)
                .collect(Collectors.toSet());
        assertTrue(hashes.size() > 1, "Should generate different hash values");
    }
}
