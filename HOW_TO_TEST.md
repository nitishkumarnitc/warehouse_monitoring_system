# How to Test the Warehouse Monitoring System

This guide shows you exactly how to test the system - both automated tests and manual testing.

## Test Results Summary

✅ **All 94 tests passing!**

- Common module: 77 tests
- Warehouse Service: 11 tests
- Central Monitoring Service: 6 tests

Build time: ~23 seconds

---

## 1. Running Unit Tests

The easiest way to verify everything works:

```bash
# Run all tests
mvn clean test
```

You should see:
```
Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Run Tests for Specific Modules

```bash
# Just the common module
cd common
mvn test

# Just the warehouse service
cd warehouse-service
mvn test

# Just the monitoring service
cd central-monitoring-service
mvn test
```

### View Test Coverage

After running tests, open the coverage reports in your browser:

```bash
# Open all coverage reports
open common/target/site/jacoco/index.html
open warehouse-service/target/site/jacoco/index.html
open central-monitoring-service/target/site/jacoco/index.html
```

---

## 2. Integration Testing (Full System)

This tests the entire system end-to-end with real Kafka.

### Step 1: Start Everything

```bash
# Start all services with Docker
docker-compose up --build -d

# Wait about 15 seconds for Kafka to be ready
sleep 15

# Check everything is running
docker-compose ps
```

You should see all 4 services "Up":
- zookeeper
- kafka (healthy)
- warehouse-service
- central-monitoring-service

### Step 2: Send Test Data

```bash
# Send a temperature reading
echo "28.5" | nc -u localhost 3344

# Send a humidity reading
echo "65.0" | nc -u localhost 3355
```

### Step 3: Verify It Worked

```bash
# Check warehouse service logs (should show "Sent to Kafka")
docker-compose logs warehouse-service | grep "Sent to Kafka"

# Check monitoring service logs (should show "Processed")
docker-compose logs central-monitoring-service | grep "Processed"
```

### Step 4: View Messages in Kafka

```bash
# See the messages in Kafka
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events \
  --from-beginning \
  --max-messages 5
```

You'll see JSON like:
```json
{
  "reading": {
    "sensorId": "sensor-3344",
    "value": 28.5,
    "sensorType": "TEMPERATURE",
    "unit": "CELSIUS",
    "timestamp": "2026-01-11T..."
  }
}
```

### Step 5: Test Error Handling

Send invalid data to see the retry mechanism:

```bash
# Send out-of-range temperature (should retry then go to DLQ)
echo "-999" | nc -u localhost 3344

# Wait for retries to happen
sleep 5

# Check the dead letter queue
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events-dlq \
  --from-beginning
```

### Step 6: Clean Up

```bash
docker-compose down
```

---

## 3. Manual Testing Scenarios

### Test Scenario 1: Normal Operation

Send a bunch of valid readings:

```bash
# Start services
docker-compose up -d

# Send 20 temperature readings
for i in {1..20}; do
  temp=$((20 + RANDOM % 15))
  echo "$temp.$((RANDOM % 10))" | nc -u localhost 3344
  sleep 0.1
done

# Check logs
docker-compose logs -f central-monitoring-service
```

### Test Scenario 2: Invalid Data

Send data that should fail validation:

```bash
# Temperature too high (max is 60°C)
echo "150" | nc -u localhost 3344

# Temperature too low (min is -40°C)
echo "-100" | nc -u localhost 3344

# Humidity out of range
echo "150" | nc -u localhost 3355

# Check the DLQ after retries
sleep 5
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events-dlq \
  --from-beginning
```

### Test Scenario 3: High Load

Test rate limiting (system limits to 1000 packets/sec):

```bash
# Send a lot of data fast
for i in {1..2000}; do
  echo "25.$((RANDOM % 10))" | nc -u localhost 3344
done &

# Check if rate limiting kicked in
docker-compose logs warehouse-service | grep -i "rate"
```

### Test Scenario 4: Wrong Input Format

The system expects plain numbers, not JSON:

```bash
# This works
echo "28.5" | nc -u localhost 3344

# This doesn't work (wrong format)
echo '{"value":28.5}' | nc -u localhost 3344

# Check logs for the error
docker-compose logs warehouse-service | grep -i "invalid"
```

---

## 4. Debugging Commands

### Check if Kafka is Working

```bash
# List all topics
docker exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list

# Check consumer lag
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group central-monitoring-group
```

### View Retry Topics

```bash
# First retry topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events-retry-1 \
  --from-beginning

# Second retry topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-events-retry-2 \
  --from-beginning
```

### Check for Errors

```bash
# Search for errors in logs
docker-compose logs | grep ERROR

# Search for warnings
docker-compose logs | grep WARN
```

---

## 5. What Each Test Validates

### Common Module Tests (77 tests)

- **SensorValidatorTest**: Temperature/humidity range validation, NaN/Infinity checks
- **SensorReadingTest**: JSON serialization, data model validation
- **RetryPublisherTest**: Kafka retry logic
- **ConfigTest**: Configuration loading and defaults
- **MetricsTest**: Prometheus metrics registration

### Warehouse Service Tests (11 tests)

- **UdpSensorListenerTest**:
  - UDP packet reception
  - Numeric value parsing
  - Invalid input handling
  - Kafka message publishing
  - Sensor type detection (temperature vs humidity)

### Central Monitoring Service Tests (6 tests)

- **SensorEventConsumerTest**:
  - Kafka message consumption
  - Event processing
  - Retry logic
  - DLQ forwarding

---

## 6. Quick Test Checklist

Use this before deploying:

- [ ] Run `mvn test` - all tests pass
- [ ] Run `docker-compose up -d` - all services start
- [ ] Send test data with `nc` - appears in logs
- [ ] Check Kafka topics - messages present
- [ ] Send invalid data - goes to DLQ
- [ ] Check logs - no unexpected errors
- [ ] Run `docker-compose down` - clean shutdown

---

## 7. Common Issues

### "Connection refused" when starting

**Cause**: Kafka isn't ready yet

**Fix**: Wait 10-15 seconds after `docker-compose up`, then restart:
```bash
docker-compose restart warehouse-service central-monitoring-service
```

### "nc: command not found"

**Fix**: Install netcat
```bash
# Mac
brew install netcat

# Ubuntu/Debian
sudo apt-get install netcat

# Use telnet as alternative
telnet localhost 3344
```

### Services keep restarting

**Check**:
```bash
docker-compose logs warehouse-service
docker-compose logs central-monitoring-service
```

Common causes:
- Kafka not ready (wait longer)
- Port conflicts (check if ports 3344, 3355 are free)
- Configuration issues

---

## 8. Performance Testing

### Measure Throughput

```bash
# Send 10,000 messages and measure time
time for i in {1..10000}; do
  echo "25.5" | nc -u localhost 3344
done

# Check how long it took and if any were dropped
docker-compose logs central-monitoring-service | grep "Processed" | wc -l
```

### Check Latency

```bash
# Send one message
echo "$(date +%s%N) 25.5" | nc -u localhost 3344

# Immediately check logs to see processing time
docker-compose logs -f central-monitoring-service
```

---

## 9. CI/CD Testing

If you're setting up automated testing:

```bash
# Clean build and test
mvn clean install

# Just run tests (faster)
mvn test

# Generate coverage report
mvn test jacoco:report

# Check coverage thresholds
mvn verify
```

---

## Need Help?

- **Unit tests failing?** Check the error message - it usually tells you exactly what's wrong
- **Integration tests not working?** Make sure Docker is running and ports aren't blocked
- **Can't see messages?** Check both producer and consumer logs - helps narrow down the issue

The system is designed to be easy to test. If something doesn't work, the logs will tell you why!
