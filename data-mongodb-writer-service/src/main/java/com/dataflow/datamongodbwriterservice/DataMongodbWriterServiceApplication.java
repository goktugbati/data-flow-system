package com.dataflow.datamongodbwriterservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class DataMongodbWriterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataMongodbWriterServiceApplication.class, args);
    }

}
