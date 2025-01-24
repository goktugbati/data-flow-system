# Data Flow System

## Overview
The Data Flow System is a microservices-based application designed to generate, process, and store data efficiently. The system consists of four main components:

1. **Data Generator Service**: Produces random data and writes to a WebSocket.
2. **Data Filter Service**: Filters and processes data, either appending to a file or sending to a message queue.
3. **Database Writer Service**: Writes filtered data from the queue to a relational database.
4. **MongoDB Writer Service**: Writes filtered data from the queue to a MongoDB collection with nested structures based on specific criteria.

---

## System Requirements

### 1. **Data Generator Service**
   - **Functionality**:
     - Generates data containing:
       - `timestamp`
       - Random integer (0-100)
       - Last 2 characters of the MD5 hash of the above values
     - Produces **5 values per second**.
     - Writes the generated data to a WebSocket.
   - **Output**: Real-time data stream sent via WebSocket.

---

### 2. **Data Filter Service**
   - **Functionality**:
     - Listens to the WebSocket for real-time data from the **Data Generator Service**.
     - Filters the incoming data based on the following logic:
       - If the `random value` > 90:
         - Sends the data to a message queue.
       - Otherwise:
         - Appends the data to a file.
   - **Output**: Filtered data is either sent to the message queue or written to a file.

---

### 3. **Message Queue Consumer Services**
#### a) **Database Writer Service**
   - **Functionality**:
     - Reads filtered data from the message queue.
     - Writes the data into a relational database table.

#### b) **MongoDB Writer Service**
   - **Functionality**:
     - Reads filtered data from the message queue.
     - Writes the data into a MongoDB collection based on the following rules:
       - If the hash value > "99":
         - Nests consecutive records into the same document.
       - Otherwise:
         - Creates a new document for each record.

---

## System Architecture

1. **Data Flow**:
   - Data is generated in real time by the **Data Generator Service**.
   - The **Data Filter Service** processes the data and routes it to either:
     - A message queue for further processing.
     - A file for storage.
   - The **Message Queue Consumer Services** consume the data:
     - **Database Writer Service** stores it in a relational database.
     - **MongoDB Writer Service** organizes it in MongoDB.

2. **Key Technologies**:
   - **WebSocket**: Used for real-time data streaming.
   - **Message Queue**: Ensures decoupled communication between services.
   - **Relational Database**: Stores structured data.
   - **MongoDB**: Stores nested data structures based on specific criteria.

---

## Setup and Execution

1. Clone the repository:
   ```bash
   git clone https://github.com/your-repo/data-flow-system.git
   cd data-flow-system
   ```

2. Build and run the system:
   ```bash
   ./gradlew build
   docker-compose up
   ```

3. Access logs for each service to monitor its behavior:
   ```bash
   docker-compose logs -f <service-name>
   ```

---

## Future Enhancements
- Add a monitoring service for real-time visualization of processed data.
- Add support for dynamic filtering criteria.

---

Feel free to modify the above structure as needed. Let me know if you'd like help formatting or adding further details!
