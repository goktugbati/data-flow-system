package com.dataflow.datamongodbwriterservice.config;

import com.dataflow.datamongodbwriterservice.repository.DataRecordMongoRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MongoDBHealthIndicator implements HealthIndicator {
    private final DataRecordMongoRepository mongoRepo;

    public MongoDBHealthIndicator(DataRecordMongoRepository mongoRepo) {
        this.mongoRepo = mongoRepo;
    }

    @Override
    public Health health() {
        try {
            mongoRepo.count();
            return Health.up()
                    .withDetail("database", "MongoDB")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "MongoDB")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}