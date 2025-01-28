package com.dataflow.dataflowsystem.filter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "storage")
@Getter
@Setter
public class FileProperties {
    private String basePath;
    private Paths paths;
    private Retention retention;
    private long flushIntervalMs;

    @Getter
    @Setter
    public static class Paths {
        private String filtered;
        private String archive;
        private String temp;
    }

    @Getter
    @Setter
    public static class Retention {
        private Integer days;
    }
}
