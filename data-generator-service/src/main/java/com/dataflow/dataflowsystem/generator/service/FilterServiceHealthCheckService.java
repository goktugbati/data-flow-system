package com.dataflow.dataflowsystem.generator.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilterServiceHealthCheckService {

    private final RestTemplate restTemplate;

    @Value("${filter-service.health-check-url}")
    private String filterServiceHealthCheckUrl;

    @CircuitBreaker(name = "filterServiceCircuitBreaker", fallbackMethod = "fallbackFilterServiceCheck")
    public boolean isFilterServiceAvailable() {
        if (filterServiceHealthCheckUrl == null || filterServiceHealthCheckUrl.isBlank()) {
            log.warn("Filter service health check URL is not set.");
            return false;
        }

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(filterServiceHealthCheckUrl, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Filter service is not available: {}", e.getMessage());
            return false;
        }
    }

    private boolean fallbackFilterServiceCheck(Exception e) {
        log.warn("Circuit breaker triggered for Filter Service: {}", e.getMessage());
        return false;
    }
}
