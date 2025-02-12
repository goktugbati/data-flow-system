# Data Flow System

## 🚀 Overview
The **Data Flow System** is a horizontally scalable microservices-based architecture designed for efficient data generation, processing, and storage. The system is built using Spring Boot and integrates with Kafka, Redis, PostgreSQL, and MongoDB for high-performance data handling.

## 🏗️ Architecture
The Data Flow System consists of **4 microservices**:

1. **Data Generator Service**
    - Generates data and sends it via WebSocket.
    - Uses Redis for batch processing and caching.

2. **Data Filter Service**
    - Filters incoming data using rule-based logic (Easy Rules).
    - Sends high-value data to Kafka for storage in databases.
    - Supports file-based storage for regular data.

3. **Data DB Writer Service**
    - Consumes Kafka messages and writes processed data to PostgreSQL.
    - Handles batch processing for efficient data insertion.
    - Uses Circuit Breakers and Retry mechanisms for resilience.
    - Integrates with Redis for intermediate message queuing.

4. **Data MongoDB Writer Service**
    - Consumes Kafka messages and stores nested records in MongoDB.
    - Implements resilience patterns using Resilience4j.
    - Handles batch processing for optimized MongoDB writes.

---

## ⚙️ Technology Stack
- **Java 17**
- **Spring Boot**
- **Kafka** (for messaging)
- **Redis** (for caching & queueing)
- **PostgreSQL & MongoDB** (for data storage)
- **Resilience4j** (for Circuit Breaker & Retry mechanisms)
- **Docker & Docker Compose** (for containerization)

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Kafka, Redis, PostgreSQL, MongoDB

### Clone the Repository
```bash
git clone https://github.com/your-repo/data-flow-system.git
cd data-flow-system
```

### Running with Docker Compose
```bash
docker-compose up -d
```

### Running Locally
```bash
./gradlew clean build
./gradlew bootRun
```

---

## 🗂️ Microservices Overview

### 1️⃣ Data Generator Service
- Generates real-time data.
- Sends data via WebSocket.
- Uses Redis for batch processing.

### 2️⃣ Data Filter Service
- Applies business rules to filter data.
- Forwards critical data to Kafka.
- Writes filtered data to files.

### 3️⃣ Data DB Writer Service
- Consumes Kafka topics.
- Writes data to PostgreSQL.
- Handles batch processing.
- Implements Circuit Breaker and Retry mechanisms.
- Integrates with Redis for stream processing.

### 4️⃣ Data MongoDB Writer Service
- Handles nested records.
- Writes data to MongoDB.
- Uses Circuit Breakers and Retry mechanisms.
- Supports batch processing for efficient data writes.

---

## ⚙️ Configuration
Configuration is managed via `application.yml` files:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

resilience4j:
  circuitbreaker:
    instances:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
  retry:
    instances:
      default:
        maxAttempts: 3
        waitDuration: 2000
```

---

## 🧪 Testing
Run unit tests:
```bash
./gradlew test
```

---

## 📊 Monitoring
- Integrated with **Prometheus** and **Grafana**.
- Exposes custom metrics for each microservice.
- Health checks via Spring Actuator.

---

## 🤝 Contributing
1. Fork the repository.
2. Create a new branch: `git checkout -b feature-xyz`
3. Make your changes and commit: `git commit -m 'Add new feature'`
4. Push to the branch: `git push origin feature-xyz`
5. Open a pull request.


---
