package com.dataflow.dataflowsystem.filter.service;

import com.dataflow.dataflowsystem.filter.config.FileProperties;
import com.dataflow.model.DataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Service
@Slf4j
public class FileWriterService {
    private final FileProperties properties;

    public FileWriterService(FileProperties properties) {
        this.properties = properties;
    }

    public void write(DataRecord record) {
        if (record == null) {
            log.error("Cannot write null record");
            return;
        }
        if (properties == null || properties.getPaths() == null) {
            log.error("File properties or paths are not properly configured");
            return;
        }

        String directoryPath = properties.getPaths().getFiltered();
        File directory = new File(directoryPath);

        if (!directory.exists()) {
            if (directory.mkdirs()) {
                log.info("Created missing directory: {}", directoryPath);
            } else {
                log.error("Failed to create directory: {}", directoryPath);
                return;
            }
        }

        String filePath = directoryPath + "/" + record.getTimestamp() + ".txt";
        writeToFile(filePath, record);
    }

    private void writeToFile(String filePath, DataRecord record) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            String line = String.format("%d,%d,%s%n",
                    record.getTimestamp(),
                    record.getRandomValue(),
                    record.getHashValue());
            writer.write(line);
            log.info("Successfully wrote record to file: {}", filePath);
        } catch (IOException e) {
            log.error("Error writing to file {}: {}", filePath, e.getMessage());
        }
    }
}
