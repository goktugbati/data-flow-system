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
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileWriterServiceTest {

    @Mock
    private FileProperties fileProperties;

    @InjectMocks
    private FileWriterService fileWriterService;

    private final Map<String, BufferedWriter> mockWriters = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);

        lenient().when(fileProperties.getFlushIntervalMs()).thenReturn(60000L);

        FileProperties.Paths paths = mock(FileProperties.Paths.class);
        lenient().when(paths.getFiltered()).thenReturn("/tmp/test-directory");
        lenient().when(fileProperties.getPaths()).thenReturn(paths);

        BufferedWriter mockWriter1 = mock(BufferedWriter.class);
        BufferedWriter mockWriter2 = mock(BufferedWriter.class);
        mockWriters.put("file1.txt", mockWriter1);
        mockWriters.put("file2.txt", mockWriter2);

        Field writersField = FileWriterService.class.getDeclaredField("writers");
        writersField.setAccessible(true);
        writersField.set(fileWriterService, mockWriters);
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

        Mockito.verify(mockWriter, Mockito.times(1)).flush(); // Verify flush was attempted
    }

    @Test
    void testWriteCreatesBufferedWriter() throws Exception {
        long testTimestamp = System.currentTimeMillis();
        DataRecordMessage record = new DataRecordMessage();
        record.setTimestamp(testTimestamp);

        fileWriterService.write(record);

        String expectedFilePath = fileWriterService.generateFilePath(record);

        Field writersField = FileWriterService.class.getDeclaredField("writers");
        writersField.setAccessible(true);
        Map<String, BufferedWriter> writers = (Map<String, BufferedWriter>) writersField.get(fileWriterService);

        assertTrue(writers.containsKey(expectedFilePath), "Expected writer for file path: " + expectedFilePath);
    }
}
