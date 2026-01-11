# Warehouse Monitoring System

A real-time sensor monitoring system for warehouses. It uses Kafka to handle sensor data coming in via UDP, with built-in retry logic and error handling.

## What It Does

This system monitors temperature and humidity sensors in a warehouse. Sensors send simple numeric values over UDP, and the system processes them through Kafka, validates the data, and handles failures gracefully.

## Tech Stack

- **Java 17** - Main programming language
- **Apache Kafka 3.7.0** - Message streaming
- **ZooKeeper** - Kafka coordination
- **Docker** - Containerization
- **Maven** - Build tool
- **Jackson** - JSON serialization
- **Logback** - Logging

## Getting Started

### What You Need

- Docker Desktop installed
- Java 17 (only if you want to run locally)
- Maven 3.9+ (for building)
- `netcat` for testing

### Running the System

```bash
# Start everything with Docker
docker-compose up --build -d

# Check if services are running
docker-compose ps
```

You should see 4 services running:
- zookeeper
- kafka
- warehouse-service
- central-monitoring-service

### Testing It Out

Send some test data to see it working:

```bash
# Send a temperature reading
echo "28.5" | nc -u localhost 3344

# Send a humidity reading
echo "65.0" | nc -u localhost 3355

# Send multiple readings
for i in {1..10}; do
  echo "$((20 + RANDOM % 10)).$((RANDOM % 10))" | nc -u localhost 3344
  sleep 0.1
done
```

### Viewing Logs

```bash
# Watch warehouse service logs
docker-compose logs -f warehouse-service

# Watch monitoring service logs
docker-compose logs -f central-monitoring-service

# See all logs
docker-compose logs -f
```

### Stopping Everything

```bash
docker-compose down
```

## How It Works

The data flow is pretty straightforward:

1. Sensors send UDP packets with numeric values (temperature or humidity)
2. Warehouse service receives them and adds metadata (sensor ID, type, timestamp)
3. Data gets published to Kafka's `sensor-events` topic
4. Monitoring service consumes the messages and validates them
5. If validation fails, the message gets retried (up to 3 times)
6. After 3 failed retries, it goes to a dead letter queue for manual review

### Retry Logic

When something fails, we don't just drop it:
- First try: `sensor-events` topic
- Retry 1: `sensor-events-retry-1` topic
- Retry 2: `sensor-events-retry-2` topic
- Give up: `sensor-events-dlq` topic (for manual investigation)

## Project Structure

```
warehouse-monitoring-system/
├── common/                          # Shared models and utilities
│   ├── SensorReading.java          # Main data model
│   ├── SensorEvent.java            # Kafka message wrapper
│   └── SensorValidator.java        # Input validation
├── warehouse-service/               # UDP listener and Kafka producer
│   └── WarehouseApplication.java   # Main entry point
├── central-monitoring-service/      # Kafka consumer and processor
│   └── CentralMonitoringApplication.java
├── docker-compose.yml              # Docker setup
└── Dockerfile                      # Container build
```

## Configuration

### Ports

- **3344** - UDP port for temperature sensors
- **3355** - UDP port for humidity sensors
- **9092** - Kafka broker
- **2181** - ZooKeeper

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `sensor-events` | Main data stream |
| `sensor-events-retry-1` | First retry |
| `sensor-events-retry-2` | Second retry |
| `sensor-events-dlq` | Failed messages |

### Environment Variables

You can customize the setup with these environment variables:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export UDP_TEMPERATURE_PORT=3344
export UDP_HUMIDITY_PORT=3355
export UDP_RATE_LIMIT=1000
```

## Building and Testing

```bash
# Build everything
mvn clean install

# Run tests
mvn test

# Run just one service locally
cd warehouse-service
mvn exec:java
```

## Monitoring

### Check Kafka Messages

```bash
# List all topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Read messages from main topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events \
  --from-beginning

# Check dead letter queue
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events-dlq \
  --from-beginning
```

### Check Consumer Lag

```bash
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group central-monitoring-group
```

## Troubleshooting

### Services won't connect to Kafka

This is normal during startup. Kafka takes 10-15 seconds to initialize. Just wait a bit and check:

```bash
docker-compose ps kafka
```

Look for "(healthy)" status.

### No messages being processed

Debug steps:

```bash
# Check if warehouse service received the data
docker-compose logs warehouse-service | grep "Sent to Kafka"

# Check if topics exist
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Look for errors
docker-compose logs central-monitoring-service | grep ERROR
```

### Sending wrong data format

The system expects plain numbers, not JSON:

```bash
# Correct
echo "28.5" | nc -u localhost 3344

# Wrong
echo '{"value":28.5}' | nc -u localhost 3344
```

## Production Features

The system includes several production-ready features:

- **Circuit breakers** on Kafka connections
- **Rate limiting** (1000 packets/sec) to prevent overload
- **Input validation** for sensor ranges
- **Graceful shutdown** with producer flushing
- **Configurable everything** via environment variables

### Sensor Validation Rules

- Temperature: -40°C to 60°C
- Humidity: 0% to 100%
- Rejects: NaN, Infinity, out-of-range values

## Example Data

What the system receives:
```
28.5
```

What gets sent to Kafka:
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

## Development

### Running Locally

```bash
# Start Kafka and ZooKeeper
docker-compose up -d kafka zookeeper

# Run warehouse service
cd warehouse-service
mvn exec:java

# In another terminal, run monitoring service
cd central-monitoring-service
mvn exec:java
```

### Running Tests

```bash
# All tests
mvn test

# With coverage report
mvn clean test jacoco:report

# View coverage
open */target/site/jacoco/index.html
```

## License

MIT License

## Author

Nitish Kumar
