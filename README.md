```md
# 🏭 Warehouse Monitoring System (Kafka-Based)

A distributed, event-driven system for ingesting real-time warehouse sensor data using **UDP + Apache Kafka**, with **retry**, **delay**, and **dead-letter queue (DLQ)** support.

---

## 📌 Overview

This system ingests sensor readings (temperature, humidity, etc.) from warehouse devices via **UDP**, publishes them to **Kafka**, and processes them asynchronously using **Kafka consumer groups**.

It is designed to be:
- Scalable
- Fault-tolerant
- Replayable
- Production-ready

---

## 🏗️ High-Level Architecture

```

┌──────────┐
│ Sensors  │
└────┬─────┘
│ UDP
▼
┌────────────────────┐
│ Warehouse Service  │
│ (Kafka Producer)   │
└────┬───────────────┘
│
▼
┌──────────────────────────────────────┐
│              Kafka                   │
│                                      │
│  sensor-events                       │
│  sensor-events-retry-5s              │
│  sensor-events-retry-30s             │
│  sensor-events-dlq                   │
└────┬─────────────────────────────────┘
│
▼
┌────────────────────────────┐
│ Central Monitoring Service │
│ (Kafka Consumer)           │
└────────────────────────────┘

```

---

## 📦 Modules

### 1️⃣ `common`
Shared domain objects:
- `SensorReading`
- `SensorEvent`
- `SensorType`
- `MeasurementUnit`

Used by all services.

---

### 2️⃣ `warehouse-service`
- Listens on UDP ports
- Parses raw sensor messages
- Publishes events to Kafka

**Key Responsibilities**
- UDP ingestion
- JSON serialization
- Kafka producer logic

---

### 3️⃣ `central-monitoring-service`
- Consumes Kafka events
- Processes sensor data
- Implements retry & DLQ logic

**Retry Strategy**
```

sensor-events
↓ failure
sensor-events-retry-5s
↓ failure
sensor-events-retry-30s
↓ failure
sensor-events-dlq

````

---

### 4️⃣ `bootstrap`
- Entry point to start the system locally
- Starts:
  - Warehouse Service
  - Central Monitoring Service

---

## 🔁 Retry & DLQ Design

| Topic | Purpose |
|-----|--------|
| `sensor-events` | Main processing |
| `sensor-events-retry-5s` | Short delay retry |
| `sensor-events-retry-30s` | Longer retry |
| `sensor-events-dlq` | Poison messages |

**Why topic-based delay?**
- No thread blocking
- Crash-safe
- Horizontally scalable
- Kafka-native

---

## 🧾 Sample Event Payload

```json
{
  "sensorId": "T-1001",
  "sensorType": "TEMPERATURE",
  "value": 32.5,
  "unit": "CELSIUS",
  "timestamp": "2026-01-10T12:30:00Z"
}
````

---

## ⚙️ Tech Stack

* Java 17
* Apache Kafka
* Jackson (JSON)
* Maven (multi-module)
* UDP (DatagramSocket)

---

## 🚀 How to Run (Local)

### 1️⃣ Start Kafka (Docker)

```bash
docker-compose up -d
```

Kafka must be running on:

```
localhost:9092
```

---

### 2️⃣ Build the Project

```bash
mvn clean install -DskipTests
```

---

### 3️⃣ Run Warehouse Service

```bash
cd warehouse-service
mvn exec:java
```

---

### 4️⃣ Run Central Monitoring Service

```bash
cd central-monitoring-service
mvn exec:java
```

---

## 📈 Scalability

* Increase Kafka partitions
* Add more consumers in the same group
* Replay data by resetting offsets

---

## 🛡️ Fault Tolerance

| Failure            | Handling            |
| ------------------ | ------------------- |
| Consumer crash     | Kafka offset replay |
| Bad message        | DLQ                 |
| Kafka broker down  | Replication         |
| Processing failure | Retry topics        |

---

## 📊 Observability (Recommended)

* Consumer lag monitoring
* DLQ size alerts
* Retry count metrics
* Structured logging with correlation IDs

---

## 🧠 Interview One一句话 (One-liner)

> *“We ingest sensor data via UDP, publish events to Kafka, process them using consumer groups with topic-based delayed retries, and guarantee reliability using DLQs and replayable streams.”*

---

## 📌 Future Improvements

* Schema Registry
* Exactly-once semantics
* Kubernetes deployment
* Prometheus + Grafana
* Kafka Streams for aggregation

---

## 👤 Author

**Nitish Kumar**
Senior Backend / AI Engineer

---

