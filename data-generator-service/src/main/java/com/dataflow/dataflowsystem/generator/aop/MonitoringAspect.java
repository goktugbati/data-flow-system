package com.dataflow.dataflowsystem.generator.aop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class MonitoringAspect {

    private final MeterRegistry meterRegistry;

    public MonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(monitorMetrics)")
    public Object monitorExecution(ProceedingJoinPoint joinPoint, MonitorMetrics monitorMetrics) throws Throwable {
        String metricName = "mq." + monitorMetrics.value() + "." + monitorMetrics.operation();
        Counter successCounter = meterRegistry.counter(metricName + ".success");
        Counter failureCounter = meterRegistry.counter(metricName + ".failure");

        try {
            Object result = joinPoint.proceed();
            successCounter.increment();
            return result;
        } catch (Throwable ex) {
            failureCounter.increment();
            log.error("Error in monitored method {} - {}", joinPoint.getSignature().getName(), ex.getMessage());
            throw ex;
        }
    }

    @AfterThrowing(pointcut = "@annotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker)", throwing = "ex")
    public void handleCircuitBreakerFailure(Throwable ex) {
        Counter circuitBreakerCounter = meterRegistry.counter("mq.circuit.breaker.opened");
        circuitBreakerCounter.increment();
        log.warn("Circuit breaker opened due to: {}", ex.getMessage());
    }
}
