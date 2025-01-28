package com.dataflow.dataflowsystem.filter;

import com.dataflow.dataflowsystem.filter.config.FileProperties;
import com.dataflow.dataflowsystem.filter.service.FileWriterService;
import com.dataflow.model.DataRecordMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileWriterServiceTest {

    @Mock
    private FileProperties fileProperties;

    @Mock
    private FileProperties.Paths paths;

    @InjectMocks
    private FileWriterService fileWriterService;

    private final Map<String, BufferedWriter> mockWriters = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        paths = new FileProperties.Paths();
        ReflectionTestUtils.setField(paths, "filtered", "/tmp/test-directory");

        lenient().when(fileProperties.getPaths()).thenReturn(paths);
        lenient().when(fileProperties.getFlushIntervalMs()).thenReturn(60000L);

        BufferedWriter mockWriter1 = mock(BufferedWriter.class);
        BufferedWriter mockWriter2 = mock(BufferedWriter.class);
        mockWriters.put("file1.txt", mockWriter1);
        mockWriters.put("file2.txt", mockWriter2);

        ReflectionTestUtils.setField(fileWriterService, "writers", mockWriters);
    }

    @Test
    void testPeriodicFlush() throws Exception {
        fileWriterService.flushAllBuffers();

        for (BufferedWriter writer : mockWriters.values()) {
            Mockito.verify(writer, Mockito.times(1)).flush();
        }
    }

    @Test
    void testFlushWithNoWriters() throws Exception {
        Field writersField = FileWriterService.class.getDeclaredField("writers");
        writersField.setAccessible(true);
        writersField.set(fileWriterService, new HashMap<>()); // Inject an empty map

        fileWriterService.flushAllBuffers();
    }

    @Test
    void testFlushWithIOException() throws Exception {
        BufferedWriter mockWriter = mock(BufferedWriter.class);
        Mockito.doThrow(new IOException("Test exception")).when(mockWriter).flush();

        mockWriters.put("fileWithError.txt", mockWriter);
        fileWriterService.flushAllBuffers();

        Mockito.verify(mockWriter, Mockito.times(1)).flush();
    }

    @Test
    void testWriteCreatesBufferedWriter() throws Exception {
        // Arrange
        String testDirectory = "/tmp/test-directory";
        long testTimestamp = System.currentTimeMillis();
        String instanceId = "TestInstance";
        String expectedFilePath = String.format("%s/%d-%s.txt", testDirectory, testTimestamp, instanceId);

        ReflectionTestUtils.setField(fileWriterService, "instanceId", instanceId);

        FileWriterService spyService = spy(fileWriterService);
        BufferedWriter mockWriter = mock(BufferedWriter.class);
        doReturn(mockWriter).when(spyService).createWriter(anyString());

        DataRecordMessage record = new DataRecordMessage();
        record.setTimestamp(testTimestamp);
        record.setRandomValue(50);
        record.setHashValue("test-hash");

        spyService.write(record);

        verify(spyService).createWriter(expectedFilePath);
        verify(mockWriter).write(contains(String.format("%d,%d,%s",
                testTimestamp,
                record.getRandomValue(),
                record.getHashValue()
        )));
    }

    @Test
    void testWriteHandlesNullRecord() {
        FileWriterService spyService = spy(fileWriterService);
        spyService.write(null);
        verify(spyService, never()).createWriter(anyString());
    }
}