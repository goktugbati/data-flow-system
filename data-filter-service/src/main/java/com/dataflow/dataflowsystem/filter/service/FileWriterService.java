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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FileWriterService {
    private static final String DATE_FORMAT = "yyyy-MM-dd-HH";
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
        createDirectories();
    }

    private void createDirectories() {
        String directoryPath = properties.getPaths().getFiltered();
        File directory = new File(directoryPath);
        if (!directory.exists() && !directory.mkdirs()) {
            log.error("Failed to create directory: {}", directoryPath);
        }
    }

    @PostConstruct
    public void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down instance: {}", instanceId);
            flushAllBuffers();
            closeAllWriters();
        }));
    }

    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanupOldWriters() {
        String currentTimeWindow = DateTimeFormatter.ofPattern(DATE_FORMAT)
                .format(Instant.now().atZone(ZoneId.systemDefault()));

        writers.entrySet().removeIf(entry -> {
            if (!entry.getKey().contains(currentTimeWindow)) {
                try {
                    entry.getValue().close();
                    log.info("Closed writer for old file: {}", entry.getKey());
                    return true;
                } catch (IOException e) {
                    log.error("Error closing writer for {}: {}", entry.getKey(), e.getMessage());
                }
            }
            return false;
        });
    }

    @Scheduled(fixedRate = 5000)
    public void periodicFlush() {
        try {
            flushAllBuffers();
        } catch (Exception e) {
            log.error("Error during periodic flush: {}", e.getMessage(), e);
        }
    }

    public synchronized void closeAllWriters() {
        writers.values().forEach(writer -> {
            try {
                writer.flush();
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
                    log.debug("Flushed buffer for file: {}", filePath);
                }
            } catch (IOException e) {
                log.error("Error flushing buffer for file {}: {}", filePath, e.getMessage());
                writers.remove(filePath);
            }
        });
    }

    public void write(DataRecordMessage record) {
        if (record == null) {
            log.error("Cannot write null record");
            return;
        }

        String filePath = generateFilePath(record);
        BufferedWriter writer = null;
        try {
            writer = writers.computeIfAbsent(filePath, this::createWriter);
            synchronized (writer) {
                writer.write(formatRecord(record));
                log.debug("Successfully wrote record to buffer: {}", filePath);
            }
        } catch (Exception e) {
            log.error("Error writing to file {}: {}", filePath, e.getMessage());
            if (writer != null) {
                writers.remove(filePath);
                try {
                    writer.close();
                } catch (IOException ioe) {
                    log.error("Error closing failed writer: {}", ioe.getMessage());
                }
            }
        }
    }

    public BufferedWriter createWriter(String filePath) {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + parentDir);
            }
            return new BufferedWriter(new FileWriter(file, true), 8192); // 8KB buffer
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

    private String generateFilePath(DataRecordMessage record) {
        String directoryPath = properties.getPaths().getFiltered();
        String timeWindow = DateTimeFormatter.ofPattern(DATE_FORMAT)
                .format(Instant.ofEpochMilli(record.getTimestamp())
                        .atZone(ZoneId.systemDefault()));

        return String.format("%s/%s-%s.txt", directoryPath, timeWindow, instanceId);
    }
}