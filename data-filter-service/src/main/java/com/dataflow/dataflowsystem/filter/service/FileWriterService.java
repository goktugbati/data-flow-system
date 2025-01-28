package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.config.FileProperties;
import com.dataflow.model.DataRecordMessage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FileWriterService {
    private final FileProperties properties;
    @Getter
    private final Map<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private final String instanceId;

    public FileWriterService(FileProperties properties) {
        this.properties = properties;
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "default-instance";
        }
        this.instanceId = System.getenv().getOrDefault("INSTANCE_ID", hostname);
        log.info("FileWriterService initialized for instance: {}", this.instanceId);
    }

    @PostConstruct
    public void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down instance: {}", instanceId);
            flushAllBuffers();
            closeAllWriters();
        }));
    }

    @Scheduled(fixedRate = 5000)
    public void periodicFlush() {
        log.info("Running periodic buffer flush");
        flushAllBuffers();
    }

    public synchronized void closeAllWriters() {
        writers.values().forEach(writer -> {
            try {
                writer.close();
            } catch (IOException e) {
                log.error("Error closing writer: {}", e.getMessage());
            }
        });
        writers.clear();
    }

    public synchronized void flushAllBuffers() {
        writers.forEach((filePath, writer) -> {
            try {
                synchronized (writer) {
                    writer.flush();
                    log.info("Flushed buffer for file: {}", filePath);
                }
            } catch (IOException e) {
                log.error("Error flushing buffer for file {}: {}", filePath, e.getMessage());
            }
        });
    }

    public void write(DataRecordMessage record) {
        if (record == null) {
            log.error("Cannot write null record");
            return;
        }

        String directoryPath = properties.getPaths().getFiltered();
        File directory = new File(directoryPath);

        if (!directory.exists() && !directory.mkdirs()) {
            log.error("Failed to create directory: {}", directoryPath);
            return;
        }

        String filePath = generateFilePath(record);

        try {
            BufferedWriter writer = writers.computeIfAbsent(filePath, this::createWriter);
            synchronized (writer) {
                writer.write(formatRecord(record));
                log.info("Successfully wrote record to buffer: {}", filePath);
            }
        } catch (Exception e) {
            log.error("Error writing to file {}: {}", filePath, e.getMessage());
        }
    }

    public BufferedWriter createWriter(String filePath) {
        try {
            return new BufferedWriter(new FileWriter(filePath, true));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create writer for file: " + filePath, e);
        }
    }

    private String formatRecord(DataRecordMessage record) {
        return String.format("%d,%d,%s%n",
                record.getTimestamp(),
                record.getRandomValue(),
                record.getHashValue());
    }

    public String generateFilePath(DataRecordMessage record) {
        String directoryPath = properties.getPaths().getFiltered();
        return String.format("%s/%d-%s.txt", directoryPath, record.getTimestamp(), instanceId);
    }

}
