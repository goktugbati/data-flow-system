package com.dataflow.dataflowsystem.filter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataFilterServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DataFilterServiceApplication.class, args);
	}

}
