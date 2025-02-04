package com.dataflow.datadbwriterservice.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class MonitoringAspect {

    private final MeterRegistry meterRegistry;

    @Around("@annotation(monitor)")
    public Object monitorOperation(ProceedingJoinPoint joinPoint, MonitorMetrics monitor) throws Throwable {
        long startTime = System.currentTimeMillis();
        String metricPrefix = monitor.value();
        String operation = monitor.operation().isEmpty() ?
                joinPoint.getSignature().getName() : monitor.operation();

        try {
            Object result = joinPoint.proceed();
            recordSuccess(metricPrefix, operation, startTime, result);
            return result;
        } catch (Exception e) {
            recordFailure(metricPrefix, operation, e);
            throw e;
        }
    }

    private void recordSuccess(String metricPrefix, String operation, long startTime, Object result) {
        long duration = System.currentTimeMillis() - startTime;
        String baseMetricName = metricPrefix + ".operations";

        meterRegistry.counter(baseMetricName,
                "operation", operation,
                "status", "success"
        ).increment();

        meterRegistry.timer(baseMetricName + ".duration",
                "operation", operation
        ).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (result instanceof Collection<?>) {
            meterRegistry.gauge(baseMetricName + ".batch_size",
                    Tags.of("operation", operation),
                    ((Collection<?>) result).size()
            );
        }
    }

    private void recordFailure(String metricPrefix, String operation, Exception e) {
        meterRegistry.counter(metricPrefix + ".operations",
                "operation", operation,
                "status", "failure",
                "error", e.getClass().getSimpleName()
        ).increment();
    }
}
