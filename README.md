# Data Flow System

## Overview
The **Data Flow System** is a microservices-based application designed to generate, process, filter, and store data efficiently using event-driven architecture. The system leverages **WebSockets**, **message queues**, and **multiple storage backends** to handle high-throughput data streams.

## System Components
The system consists of the following microservices:

### 1. **Data Generator Service**
- **Functionality**:
    - Generates data records containing:
        - `timestamp`
        - Random integer (0-100)
        - Last 2 characters of the MD5 hash of the above values.
    - Produces **5 records per second**.
    - Sends the generated data to the **Data Filter Service** via WebSocket.
    - **Circuit Breaker**: Stops sending data if the **Filter Service** is unavailable.

### 2. **Data Filter Service**
- **Functionality**:
    - Receives real-time data from **Data Generator Service** via WebSocket.
    - Applies filtering logic:
        - If `random value > 90`: Sends data to **RabbitMQ** for further processing.
        - Otherwise: Appends the data to a file (**Buffered Writing** for efficiency).
    - **Circuit Breaker**: Ensures stability if the **message queue** or **file system** is down.

### 3. **Message Queue Consumer Services**
#### a) **Database Writer Service**
- **Functionality**:
    - Consumes filtered data from **RabbitMQ**.
    - Stores the data into a **relational database (PostgreSQL)**.
    - **Resilience Features**:
        - **Circuit Breaker** to handle **database failures**.
        - **Retry mechanism** with exponential backoff.
        - **Metrics and monitoring** for database operations.

#### b) **MongoDB Writer Service**
- **Functionality**:
    - Consumes filtered data from **RabbitMQ**.
    - Stores the data into **MongoDB**, applying nested document rules:
        - If `hashValue > "99"`: Nest consecutive records into the same document.
        - Otherwise: Create a new document.
    - **Resilience Features**:
        - **Circuit Breaker** to handle **MongoDB unavailability**.
        - **Metrics tracking failures and latencies**.

### 4. **Monitoring and Observability**
- **Metrics collection** using **Micrometer & Prometheus**.
- **Logging & tracing** integrated via **Spring AOP**.
- **Health checks** for each service to ensure uptime.

---
## System Architecture
### Data Flow Overview
1. **Data Generator Service** streams real-time data via WebSocket.
2. **Data Filter Service** processes data and routes it:
    - **Message Queue** for high-value data.
    - **File system** for other records.
3. **Database Writer Service** and **MongoDB Writer Service** consume messages from the queue and store data.
4. **Monitoring & Circuit Breakers** ensure resilience and observability.

### Key Technologies Used
- **Spring Boot** for microservices.
- **WebSocket** for real-time data streaming.
- **RabbitMQ** as a message broker.
- **PostgreSQL & MongoDB** for structured and unstructured data.
- **Resilience4j** for circuit breakers and fault tolerance.
- **Micrometer & Prometheus** for monitoring.
- **Docker & Docker Compose** for containerized deployment.

---
## Setup and Execution

### 1. Clone the repository:
```bash
git clone https://github.com/your-repo/data-flow-system.git
cd data-flow-system
```

### 2. Build and run the system:
```bash
./gradlew build
docker-compose up
```

### 3. Monitor logs:
```bash
docker-compose logs -f <service-name>
```

### 4. Run tests:
```bash
./gradlew test
```

---
## Future Enhancements
- Add a **stream processing layer** (Kafka or Flink) for complex event processing.
- Implement **dynamic filtering** with a rule engine.
- Extend **alerting mechanisms** for system failures.
- Introduce **API Gateway** for centralized management.

---
This **Data Flow System** is designed for **scalability, resilience, and efficiency**, making it ideal for real-time data processing needs.

