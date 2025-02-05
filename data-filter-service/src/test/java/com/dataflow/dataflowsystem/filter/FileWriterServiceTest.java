package com.dataflow.dataflowsystem.filter;

import com.dataflow.dataflowsystem.filter.config.FileProperties;
import com.dataflow.dataflowsystem.filter.service.FileWriterService;
import com.dataflow.model.DataRecordMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileWriterServiceTest {

    @Mock
    private FileProperties fileProperties;

    private FileWriterService fileWriterService;
    private Map<String, BufferedWriter> mockWriters;

    @BeforeEach
    void setup() throws Exception {
        FileProperties.Paths paths = new FileProperties.Paths();
        paths.setFiltered("/tmp/test-directory");
        lenient().when(fileProperties.getPaths()).thenReturn(paths);
        lenient().when(fileProperties.getFlushIntervalMs()).thenReturn(60000L);

        fileWriterService = new FileWriterService(fileProperties);

        mockWriters = new ConcurrentHashMap<>();
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
        writersField.set(fileWriterService, new HashMap<>());

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
        ReflectionTestUtils.setField(fileWriterService, "instanceId", "TestInstance");

        DataRecordMessage record = new DataRecordMessage();
        record.setTimestamp(Instant.now().toEpochMilli());
        record.setRandomValue(50);
        record.setHashValue("test-hash");

        FileWriterService spyService = spy(fileWriterService);
        BufferedWriter mockWriter = mock(BufferedWriter.class);
        doReturn(mockWriter).when(spyService).createWriter(anyString());

        spyService.write(record);

        // Updated file path to include minutes
        String expectedFilePath = String.format("/tmp/test-directory/%s-TestInstance.txt",
                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm") // Now grouping by minute
                        .format(Instant.ofEpochMilli(record.getTimestamp()).atZone(ZoneId.systemDefault()))
        );
        verify(spyService).createWriter(expectedFilePath);
        verify(mockWriter).write(contains("50,test-hash"));
    }


    @Test
    void testWriteHandlesNullRecord() {
        FileWriterService spyService = spy(fileWriterService);
        spyService.write(null);
        verify(spyService, never()).createWriter(anyString());
    }
}
