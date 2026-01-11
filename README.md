# Warehouse Monitoring System

A distributed event-driven system for real-time warehouse sensor monitoring. Built with Kafka for reliable message streaming, this system handles sensor data ingestion via UDP and implements robust retry logic with dead letter queues.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![Kafka](https://img.shields.io/badge/Kafka-3.7.0-black.svg)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [System Behavior](#system-behavior)
- [Monitoring & Observability](#monitoring--observability)
- [Troubleshooting](#troubleshooting)
- [Production Deployment](#production-deployment)

---

## Architecture Overview

The system has three core components:

1. **Warehouse Service** - Listens for sensor data on UDP ports and publishes events to Kafka
2. **Central Monitoring Service** - Consumes and processes events from Kafka with built-in retry logic
3. **Kafka Infrastructure** - Message broker backed by ZooKeeper for coordination

### How It Works

Here's the data flow in simple terms:

1. **Sensors send data** → UDP packets on port 3344 (temperature) or 3355 (humidity)
2. **Warehouse Service receives** → Listens on those UDP ports, gets the readings
3. **Enriches and sends to Kafka** → Adds metadata and publishes to `sensor-events` topic
4. **Kafka stores the messages** → Keeps them safe with retry topics if needed
5. **Monitoring Service processes** → Consumes messages and validates them
6. **Retry on failure** → If something fails, tries again up to 3 times
7. **Dead letter queue** → After 3 failures, sends to DLQ for manual review

The retry flow looks like this:
- Try 1: `sensor-events` topic
- Try 2: `sensor-events-retry-1` topic
- Try 3: `sensor-events-retry-2` topic
- Give up: `sensor-events-dlq` topic (dead letter queue)

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Build Tool | Maven | 3.9.6 |
| Message Broker | Apache Kafka | 3.7.0 |
| Coordination | Apache ZooKeeper | 7.6.1 |
| Serialization | Jackson | 2.17.2 |
| Logging | SLF4J + Logback | 1.4.11 |
| Containerization | Docker | 20.10+ |
| Orchestration | Docker Compose | 2.0+ |

---

## Quick Start

### Prerequisites

- Docker Desktop or Docker Engine (20.10+)
- Docker Compose (2.0+)
- Java 17 (for local development)
- Maven 3.9+ (for local development)
- netcat (`nc`) for testing

### 1. Start All Services

```bash
# Clone the repository
cd warehouse-monitoring-system

# Build and start all services
docker-compose up --build -d

# Verify all services are running
docker-compose ps
```

Expected output:
```
NAME                         STATUS
central-monitoring-service   Up
kafka                        Up (healthy)
warehouse-service            Up
zookeeper                    Up
```

### 2. Send Test Sensor Data

The system listens on two UDP ports. Just send plain numeric values:

```bash
# Temperature reading (port 3344)
echo "28.5" | nc -u localhost 3344

# Humidity reading (port 3355)
echo "65.0" | nc -u localhost 3355

# Or send a bunch at once
for i in {1..10}; do
  echo "$((20 + RANDOM % 10)).$((RANDOM % 10))" | nc -u localhost 3344
  sleep 0.1
done
```

### 3. View Logs

```bash
# View warehouse service logs (producer)
docker-compose logs -f warehouse-service

# View monitoring service logs (consumer)
docker-compose logs -f central-monitoring-service

# View all logs
docker-compose logs -f
```

Expected output:

**Warehouse Service:**
```
Warehouse Service started
Listening on UDP port 3344
Listening on UDP port 3355
Sent to Kafka: {"reading":{"sensorId":"sensor-3344","value":28.5,...}}
```

**Central Monitoring Service:**
```
Central Monitoring Service started
Processed: SensorReading{sensorId='sensor-3344', value=28.5°C, sensorType=TEMPERATURE, timestamp=2026-01-10T...}
```

### 4. Stop All Services

```bash
docker-compose down
```

---

## Project Structure

This is a Maven multi-module project with 4 main modules:

### 1. common/
Shared code used by both services. Contains:
- `SensorReading.java` - The main data model (sensor ID, value, type, timestamp)
- `SensorEvent.java` - Wrapper for Kafka messages
- `SensorType.java` - Enum for TEMPERATURE or HUMIDITY
- `MeasurementUnit.java` - Enum for CELSIUS or FAHRENHEIT
- `KafkaHeaders.java` - Constants for Kafka headers like RETRY_COUNT

### 2. warehouse-service/
Receives sensor data via UDP and sends to Kafka. Key files:
- `WarehouseApplication.java` - Main entry point, starts 2 UDP listeners
- `UdpSensorListener.java` - Listens on UDP port and publishes to Kafka
- `KafkaProducerFactory.java` - Creates Kafka producer with proper config

### 3. central-monitoring-service/
Consumes from Kafka and processes events. Key files:
- `CentralMonitoringApplication.java` - Main entry point, starts 3 consumer threads
- `SensorEventConsumer.java` - Polls Kafka and processes messages
- `RetryPublisher.java` - Publishes failed messages to retry topics
- `KafkaConsumerFactory.java` - Creates Kafka consumer with proper config

### 4. bootstrap/
Optional launcher module that can start both services together:
- `BootstrapApplication.java` - Main entry point
- `SystemLauncher.java` - Coordinates starting warehouse and monitoring services

### Other Files
- `docker-compose.yml` - Runs everything (ZooKeeper, Kafka, both services)
- `Dockerfile` - Builds the Java services
- `pom.xml` - Parent Maven configuration

---

## Configuration

### Kafka Topics

| Topic | Purpose | Producer | Consumer | Retention |
|-------|---------|----------|----------|-----------|
| `sensor-events` | Primary sensor data stream | warehouse-service | central-monitoring-service | 7 days |
| `sensor-events-retry-1` | First retry attempt | central-monitoring-service | central-monitoring-service | 7 days |
| `sensor-events-retry-2` | Second retry attempt | central-monitoring-service | central-monitoring-service | 7 days |
| `sensor-events-dlq` | Dead letter queue (after 3 retries) | central-monitoring-service | Manual processing | 30 days |

### UDP Port Mapping

| Port | Protocol | Sensor Type | Example Value | Container Port |
|------|----------|-------------|---------------|----------------|
| 3344 | UDP | Temperature | `28.5` | 3344 |
| 3355 | UDP | Humidity | `65.0` | 3355 |

### Kafka Configuration

**Producer Configuration** (`warehouse-service`):
```properties
bootstrap.servers=kafka:9092
key.serializer=StringSerializer
value.serializer=StringSerializer
acks=all                        # Wait for all replicas
retries=3                       # Retry on failure
max.in.flight.requests=1        # Preserve order
```

**Consumer Configuration** (`central-monitoring-service`):
```properties
bootstrap.servers=kafka:9092
group.id=central-monitoring-group
key.deserializer=StringDeserializer
value.deserializer=StringDeserializer
auto.offset.reset=earliest      # Start from beginning
enable.auto.commit=true         # Auto-commit offsets
auto.commit.interval.ms=1000
```

---

## System Behavior

### Data Flow (End-to-End)

1. **Ingestion**: Sensor devices send numeric values via UDP (e.g., `28.5`)
2. **Enrichment**: Warehouse service enriches data with metadata:
   - Sensor ID (derived from port: `sensor-3344`)
   - Sensor Type (`TEMPERATURE` for 3344, `HUMIDITY` for 3355)
   - Measurement Unit (`CELSIUS`)
   - Timestamp (`Instant.now()`)
3. **Serialization**: Convert to JSON with Jackson (including Java 8 Instant timestamps)
4. **Publishing**: Send to Kafka topic `sensor-events`
5. **Consumption**: Central monitoring service polls with 500ms intervals
6. **Validation**: Check sensor values (fails if value < 0)
7. **Processing**: Log successful processing
8. **Retry Logic**: Failed messages retry up to 3 times
9. **Dead Letter**: After 3 retries, send to DLQ for manual investigation

### Retry Mechanism

When a message fails to process, we don't just drop it. Instead, we use separate Kafka topics for retries:

- **First attempt**: Message arrives on `sensor-events` topic
- **If it fails**: Republish to `sensor-events-retry-1` (retry count = 1)
- **If it fails again**: Republish to `sensor-events-retry-2` (retry count = 2)
- **If it still fails**: Send to `sensor-events-dlq` (dead letter queue for manual review)

We track the retry count using a Kafka message header called `RETRY_COUNT`. Each retry topic has its own consumer thread, so they all process in parallel.

### Error Handling

| Error Type | Behavior | Example |
|------------|----------|---------|
| Invalid sensor value (< 0) | Retry up to 3 times, then DLQ | `-5.0` → Retry → DLQ |
| JSON serialization error | Log error and crash (fail-fast) | Invalid Instant format |
| Kafka connection lost | Auto-reconnect with exponential backoff | Network partition |
| UDP receive error | Log error, continue listening | Malformed packet |
| NumberFormatException | Log error, drop message | `{"invalid":"json"}` |

### Sample Event Payload

**Input (UDP)**:
```
28.5
```

**Output (Kafka)**:
```json
{
  "reading": {
    "sensorId": "sensor-3344",
    "value": 28.5,
    "sensorType": "TEMPERATURE",
    "unit": "CELSIUS",
    "timestamp": "2026-01-10T15:03:38.374724591Z"
  }
}
```

---

## Monitoring & Observability

### Health Checks

```bash
# Check all services
docker-compose ps

# Check Kafka health
docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check service logs for errors
docker-compose logs | grep ERROR

# View Kafka topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Kafka Topic Inspection

```bash
# Describe topic
docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --describe --topic sensor-events

# Consume messages from topic
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic sensor-events --from-beginning

# Check consumer group lag
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group central-monitoring-group

# View DLQ messages
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic sensor-events-dlq --from-beginning
```

### Key Metrics to Monitor

| Metric | Tool | Alert Threshold |
|--------|------|-----------------|
| Consumer Lag | Kafka Consumer Groups | > 10000 messages |
| DLQ Size | Kafka Topic Size | > 100 messages |
| Retry Rate | Application Logs | > 5% of total |
| Processing Latency | Application Metrics | > 1 second |
| UDP Packet Loss | Network Stats | > 1% |

---

## Troubleshooting

### Services won't start

**Symptom**: `Connection to node -1 (kafka/172.19.0.3:9092) could not be established`

**Solution**: This is totally normal during the first 10-15 seconds of startup. Kafka just needs time to initialize. Wait for the health check to pass.

```bash
# Check Kafka health status
docker-compose ps kafka

# Wait and retry
docker-compose restart warehouse-service central-monitoring-service
```

### No messages being processed

**Symptom**: Sent UDP data but nothing shows up in the monitoring service logs

**How to debug**:
```bash
# 1. Check warehouse service is receiving data
docker-compose logs warehouse-service | grep "Sent to Kafka"

# 2. Check Kafka topics exist
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# 3. Verify consumer group is active
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group central-monitoring-group

# 4. Check for errors in monitoring service
docker-compose logs central-monitoring-service | grep ERROR
```

### Jackson serialization errors

**Symptom**: `Java 8 date/time type not supported by default`

**What's wrong**: Either the `jackson-datatype-jsr310` dependency is missing, or you forgot to register the `JavaTimeModule`

**Fix it**:
```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
```

Check dependencies:
```bash
mvn dependency:tree | grep jackson
```

### Issue: Services keep restarting

**Symptom**: Container exits with code 143

**Diagnosis**:
```bash
# Check application logs
docker-compose logs warehouse-service
docker-compose logs central-monitoring-service

# Check container resource usage
docker stats
```

**Common Causes**:
- Missing dependencies in fat JAR (check maven-shade-plugin)
- Invalid Kafka configuration
- Port conflicts
- Out of memory (increase JVM heap)

### Invalid JSON input error

**Symptom**: `NumberFormatException: For input string: "{"sensorId":"T-1"...}"`

**What happened**: You're sending JSON to the UDP port, but it expects just a plain number

**The fix**:
```bash
# Correct
echo "28.5" | nc -u localhost 3344

# Wrong
echo '{"sensorId":"T-1","value":28.5}' | nc -u localhost 3344
```

---

## Production Deployment

### Environment Variables

```bash
# Application Configuration
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
LOG_LEVEL=INFO

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
KAFKA_CONSUMER_GROUP_ID=central-monitoring-group
KAFKA_REPLICATION_FACTOR=3
KAFKA_MIN_INSYNC_REPLICAS=2

# Service Ports
WAREHOUSE_SERVICE_TEMP_PORT=3344
WAREHOUSE_SERVICE_HUMID_PORT=3355

# Monitoring
ENABLE_JMX=true
JMX_PORT=9999
```

### Docker Compose (Production)

```yaml
version: '3.8'

services:
  warehouse-service:
    image: warehouse-service:1.0.0
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
      restart_policy:
        condition: on-failure
        max_attempts: 3
    ports:
      - "3344:3344/udp"
      - "3355:3355/udp"
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JAVA_OPTS=-Xms1g -Xmx2g
    healthcheck:
      test: ["CMD", "nc", "-zvu", "localhost", "3344"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - monitoring-network
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

  central-monitoring-service:
    image: central-monitoring-service:1.0.0
    deploy:
      replicas: 5
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - KAFKA_CONSUMER_GROUP_ID=central-monitoring-group
      - JAVA_OPTS=-Xms1g -Xmx2g
    depends_on:
      kafka:
        condition: service_healthy
    networks:
      - monitoring-network

networks:
  monitoring-network:
    driver: bridge
```

### Security Considerations

1. **Network Isolation**: Run services in private VPC
2. **Kafka Authentication**: Enable SASL/SCRAM or mTLS
3. **Encryption**: TLS for Kafka communication
4. **Input Validation**: Validate all UDP inputs
5. **Resource Limits**: Prevent resource exhaustion
6. **Secrets Management**: Use HashiCorp Vault or AWS Secrets Manager

### Monitoring Stack

```yaml
# Prometheus metrics
prometheus:
  scrape_configs:
    - job_name: 'warehouse-service'
      static_configs:
        - targets: ['warehouse-service:9090']
    - job_name: 'central-monitoring-service'
      static_configs:
        - targets: ['monitoring-service:9090']
    - job_name: 'kafka'
      static_configs:
        - targets: ['kafka:9999']

# Grafana dashboards
grafana:
  datasources:
    - name: Prometheus
      type: prometheus
      url: http://prometheus:9090
  dashboards:
    - warehouse-monitoring-system
```

### Key Metrics

| Metric | Type | Alert Threshold |
|--------|------|-----------------|
| `kafka_consumer_lag` | Gauge | > 10000 |
| `udp_packets_received` | Counter | - |
| `udp_packets_dropped` | Counter | > 100/min |
| `kafka_producer_errors` | Counter | > 10/min |
| `kafka_consumer_errors` | Counter | > 10/min |
| `dlq_message_count` | Gauge | > 100 |
| `jvm_memory_used` | Gauge | > 80% |
| `processing_latency_p99` | Histogram | > 1000ms |

---

## License

This project is licensed under the MIT License.

---

## Author

**Nitish Kumar**
Senior Backend Engineer

---

Built with Java 17, Apache Kafka, and Docker
