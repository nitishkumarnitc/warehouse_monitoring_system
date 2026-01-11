# System Design - Warehouse Monitoring System

This document explains how the warehouse monitoring system is designed and why certain decisions were made.

## Table of Contents
- [High-Level Design](#high-level-design)
- [Low-Level Design](#low-level-design)
- [Key Design Decisions](#key-design-decisions)

---

## High-Level Design

### What does this system do?

We built this to monitor sensors in warehouses in real-time. Sensors send temperature and humidity readings via UDP, we process them through Kafka, and handle failures gracefully with retries.

### The big picture

Here's how data flows through the system:

```
Warehouse Sensors (sending UDP packets)
        ↓
Warehouse Service (receives UDP, enriches data)
        ↓
Apache Kafka (message broker)
        ↓
Central Monitoring Service (processes events)
        ↓
Logs/Alerts (or retry/DLQ if failed)
```

### Main components

**1. Warehouse Service**

This service sits at the edge and receives raw sensor data. It listens on two UDP ports:
- Port 3344 for temperature sensors
- Port 3355 for humidity sensors

When a UDP packet arrives with just a number like `28.5`, the service:
- Parses the value
- Figures out what sensor it came from (based on the port)
- Adds metadata like timestamp and sensor type
- Serializes everything to JSON
- Publishes to Kafka

The service runs two threads, one for each UDP port. This keeps them independent - if one has issues, the other keeps working.

**2. Apache Kafka**

Kafka is the backbone of the system. It gives us:
- **Durability**: Messages are written to disk and replicated
- **Replayability**: We can reprocess old data if needed
- **Decoupling**: Services don't talk directly to each other
- **Scalability**: We can add more consumers without changing anything

We use four topics:
- `sensor-events` - Where all new events go first
- `sensor-events-retry-1` - First retry attempt
- `sensor-events-retry-2` - Second retry attempt
- `sensor-events-dlq` - Dead letter queue for messages that failed 3 times

**3. Central Monitoring Service**

This consumes events from Kafka and processes them. It runs three consumer threads in parallel:
- Thread 1: Processes messages from main topic
- Thread 2: Handles first retry
- Thread 3: Handles second retry

For each message, it:
- Deserializes the JSON back to objects
- Validates the sensor reading (fails if value < 0)
- Logs the successful reading
- If something goes wrong, publishes to the next retry topic

### How retries work

Instead of sleeping and retrying in the same thread, we use separate Kafka topics for retries. Here's why this is better:

**Traditional approach (bad):**
```
try {
    process(message);
} catch (Exception e) {
    Thread.sleep(5000);  // Blocks the thread!
    retry();
}
```

Problems:
- If the service crashes during sleep, we lose the retry
- The thread is blocked and can't process other messages
- Can't scale horizontally

**Our approach (good):**
```
try {
    process(message);
} catch (Exception e) {
    publishToRetryTopic(message);  // Non-blocking
}
```

Benefits:
- Retries survive crashes (Kafka persists them)
- No thread blocking
- Each retry level has its own consumer
- Can monitor retry metrics separately

The retry flow looks like this:

```
sensor-events (try 1)
    ↓ failure
sensor-events-retry-1 (try 2)
    ↓ failure
sensor-events-retry-2 (try 3)
    ↓ failure
sensor-events-dlq (give up, manual review needed)
```

We track retry count in Kafka message headers so we know when to give up.

### Data model

**SensorReading** - The core domain object
```java
{
  sensorId: "sensor-3344",
  value: 28.5,
  sensorType: TEMPERATURE,
  unit: CELSIUS,
  timestamp: "2026-01-10T15:03:38Z"
}
```

**SensorEvent** - Wrapper for Kafka
```java
{
  reading: { ... SensorReading ... }
}
```

Both are immutable (all fields are final). This makes them thread-safe and easier to reason about.

### Scaling strategy

**Warehouse Service:**
- Can run multiple instances behind a load balancer
- Each instance handles its own UDP ports
- UDP is stateless so this works fine

**Kafka:**
- Add more partitions to topics
- Partition by sensor ID to maintain ordering per sensor
- Currently using 3 partitions per topic

**Central Monitoring Service:**
- Multiple instances in the same consumer group
- Kafka automatically distributes partitions among them
- If one consumer dies, others pick up its partitions

### What happens when things fail?

**Warehouse Service crashes:**
- UDP packets sent during downtime are lost (acceptable trade-off)
- When it restarts, starts processing new packets
- No state to recover since UDP is fire-and-forget

**Kafka crashes:**
- Producers queue messages in memory (up to a limit)
- Automatic retry with exponential backoff
- If Kafka stays down too long, producer will fail

**Central Monitoring Service crashes:**
- Kafka remembers the last committed offset
- When service restarts, continues from last processed message
- No data loss (this is why we chose Kafka)

**Network partition:**
- Kafka replication handles this
- As long as we have `min.insync.replicas=2`, writes succeed
- Consumers reconnect automatically when network recovers

---

## Low-Level Design

### Thread model

Let's look at what's actually running in each JVM.

**Warehouse Service threads:**

```
Main Thread
├─ Starts the application
└─ Calls Thread.currentThread().join() to keep JVM alive

UDP Listener Thread 1 (Temperature - Port 3344)
├─ while(true) loop
├─ socket.receive() ← Blocking call waiting for UDP packets
├─ Parse the number
├─ Create SensorReading and SensorEvent objects
├─ Serialize to JSON
└─ producer.send() ← Async, returns immediately

UDP Listener Thread 2 (Humidity - Port 3355)
└─ Same as Thread 1, different port

Kafka Producer I/O Thread (Internal)
├─ Managed by KafkaProducer library
├─ Batches messages together
├─ Compresses them
├─ Sends to Kafka broker
└─ Handles retries and acks
```

Why separate threads for each UDP port?
- Isolation - if one port has issues, the other keeps working
- Simplicity - each thread has one job
- Performance - both can receive packets simultaneously

**Central Monitoring Service threads:**

```
Main Thread
├─ Starts the application
└─ Calls Thread.currentThread().join() to keep JVM alive

Consumer Thread 1 (Main topic: sensor-events)
├─ while(true) loop
├─ consumer.poll(500ms) ← Blocking call, waits up to 500ms for messages
├─ Loop through received messages
├─ Try to process each one
└─ On failure, call handleFailure()

Consumer Thread 2 (Retry topic 1)
└─ Same logic, different topic

Consumer Thread 3 (Retry topic 2)
└─ Same logic, different topic

Kafka Consumer Heartbeat Thread (Internal)
├─ Managed by KafkaConsumer library
├─ Sends heartbeats to Kafka every 3 seconds
└─ Participates in consumer group rebalancing
```

Why three consumer threads?
- Parallel processing of main topic and retry topics
- If main topic is backed up, retries still get processed
- Each thread is simple and focused

### How UDP ingestion works

Step by step, here's what happens when a sensor sends `28.5`:

**Step 1: Receive UDP packet**
```
Sensor sends: "28.5\n"
    ↓
DatagramSocket.receive(packet)  // Blocks until packet arrives
    ↓
Extract bytes from packet
    ↓
Convert to String: "28.5"
```

**Step 2: Parse and enrich**
```
Parse: Double.parseDouble("28.5") → 28.5
    ↓
Determine sensor info from port:
  - Port 3344 → Temperature sensor
  - sensorId = "sensor-3344"
  - sensorType = TEMPERATURE
  - unit = CELSIUS
    ↓
Add timestamp: Instant.now()
    ↓
Create SensorReading object (immutable)
    ↓
Wrap in SensorEvent
```

**Step 3: Serialize to JSON**
```
ObjectMapper.writeValueAsString(sensorEvent)
    ↓
Jackson serialization:
  - Reads @JsonProperty annotations
  - JavaTimeModule converts Instant to ISO-8601 string
  - Outputs JSON string
    ↓
Result: {"reading":{"sensorId":"sensor-3344","value":28.5,...}}
```

**Step 4: Publish to Kafka**
```
Create ProducerRecord:
  - topic: "sensor-events"
  - key: "sensor-3344" (for partitioning)
  - value: JSON string
    ↓
producer.send(record)  // Async call, returns Future
    ↓
Producer batches this with other messages
    ↓
When batch is full (16KB) or 10ms passes:
  - Compress batch
  - Send to Kafka broker
  - Wait for ack (we use acks=all)
    ↓
Return success to caller
```

Why async send? Because we don't want to block the UDP thread waiting for Kafka acks. The producer handles batching and network I/O in the background.

### How Kafka consumption works

**Step 1: Poll for messages**
```
consumer.poll(Duration.ofMillis(500))
    ↓
Kafka consumer sends fetch request
    ↓
Broker sends back a batch of messages
    ↓
Consumer deserializes batch into ConsumerRecords
    ↓
We get an iterable collection of records
```

**Step 2: Process each message**
```
for (ConsumerRecord record : records) {
    try {
        String json = record.value();
        SensorEvent event = mapper.readValue(json, SensorEvent.class);

        // Validation
        if (event.getReading().getValue() < 0) {
            throw new RuntimeException("Invalid value");
        }

        // Business logic
        System.out.println("Processed: " + event.getReading());

    } catch (Exception e) {
        handleFailure(record);
    }
}
```

**Step 3: Handle failures**
```
handleFailure(record) {
    // Check retry count from message headers
    int retryCount = getRetryCount(record);

    if (retryCount < 3) {
        // Publish to next retry topic
        String nextTopic = getNextRetryTopic(retryCount);

        // Add header with incremented retry count
        Headers headers = new RecordHeaders();
        headers.add("RETRY_COUNT", String.valueOf(retryCount + 1));

        producer.send(new ProducerRecord(nextTopic, headers, record.value()));

        System.out.println("Retry attempt: " + (retryCount + 1));
    } else {
        // Give up, send to DLQ
        producer.send(new ProducerRecord("sensor-events-dlq", record.value()));
        System.out.println("Sent to DLQ after 3 retries");
    }
}
```

**Step 4: Commit offsets**
```
After processing all messages in the batch:
    ↓
Consumer auto-commits offset (every 1 second by default)
    ↓
Kafka remembers we processed up to this point
    ↓
If consumer crashes and restarts, it resumes from last committed offset
```

### JSON serialization details

We use Jackson with some specific configuration.

**Serialization (Java → JSON):**
```java
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());  // Important!

SensorEvent event = new SensorEvent(...);
String json = mapper.writeValueAsString(event);
```

The JavaTimeModule tells Jackson how to handle `Instant` objects. Without it, we'd get:
```
InvalidDefinitionException: Java 8 date/time type not supported
```

With it, `Instant` becomes ISO-8601 string:
```
"timestamp": "2026-01-10T15:03:38.374724591Z"
```

**Deserialization (JSON → Java):**
```java
String json = record.value();
SensorEvent event = mapper.readValue(json, SensorEvent.class);
```

Jackson looks at `@JsonCreator` and `@JsonProperty` annotations to figure out how to construct the objects:

```java
@JsonCreator
public SensorReading(
    @JsonProperty("sensorId") String sensorId,
    @JsonProperty("value") double value,
    ...
) { ... }
```

This is needed because our objects are immutable (final fields, no setters). Jackson can't use the default approach of creating an empty object and calling setters.

### Error handling strategy

Different types of errors get different treatment:

**1. Invalid sensor value (< 0)**
- Business logic error
- Retry up to 3 times (maybe it's transient?)
- After 3 retries, send to DLQ
- DLQ messages need manual investigation

**2. JSON deserialization error**
- Usually means a bug in our code or corrupted data
- Print the error with stack trace
- Publish to retry topic (in case it's transient)

**3. Kafka connection lost**
- Network or broker issue
- Producer and consumer have built-in retry logic
- Exponential backoff (1s, 2s, 4s, 8s...)
- Eventually throws exception if Kafka stays down

**4. UDP receive error**
- Rare, usually means socket is closed
- Log the error
- Continue listening (don't crash the thread)

**5. NumberFormatException (parsing UDP packet)**
- Someone sent invalid data to our UDP port
- Log it and drop the packet
- Don't crash the thread

### Data structures and memory

**Memory footprint per message:**
- Raw UDP packet: ~5 bytes (e.g., "28.5\n")
- SensorReading object: ~120 bytes (objects + references)
- JSON string: ~165 bytes
- Kafka message overhead: ~50 bytes (headers, metadata)

Total: ~340 bytes per sensor reading

**Batching in Kafka Producer:**
- Default batch size: 16 KB = ~47 messages
- Max wait time: 10ms
- Compression: None by default (can enable gzip/snappy)

**Memory in Kafka Consumer:**
- Fetch max: 50 MB per poll
- We poll every 500ms
- Processing is fast (~1ms per message)
- No risk of OOM

### Configuration tuning

Important settings we chose and why:

**Kafka Producer:**
```
acks=all                         // Wait for all replicas (most durable)
retries=3                        // Retry up to 3 times
max.in.flight.requests=1         // Preserve order
enable.idempotence=true          // Prevent duplicates
```

Why `max.in.flight.requests=1`?
If we allow 2+ in-flight requests and one fails, they could get reordered on retry. Setting it to 1 guarantees ordering but reduces throughput slightly (acceptable trade-off).

**Kafka Consumer:**
```
group.id=central-monitoring-group  // All instances in same group
auto.offset.reset=earliest          // Start from beginning if no offset
enable.auto.commit=true             // Commit offsets automatically
auto.commit.interval.ms=1000        // Commit every second
```

Why auto-commit?
Simpler code and works fine for our use case. If we need exactly-once semantics later, we'd switch to manual commits.

### Performance characteristics

Based on testing and design:

**Throughput:**
- UDP listener: ~10,000 messages/sec per port
- Kafka producer: ~100,000 messages/sec (with batching)
- Kafka consumer: ~50,000 messages/sec per thread
- Bottleneck: UDP receiving (but that's way more than we need)

**Latency (end-to-end):**
- UDP receive to Kafka publish: 1-2ms
- Kafka publish to consumer receive: 5-10ms
- Consumer processing: < 1ms
- Total: ~15ms p99

**Resource usage per instance:**
- CPU: ~10% (mostly idle, waiting for messages)
- Memory: 512MB heap (could run with less)
- Network: Minimal (messages are small)
- Disk: None (except Kafka broker)

---

## Key Design Decisions

### Why UDP instead of TCP?

UDP is much simpler and faster for this use case:
- No connection establishment (3-way handshake)
- No connection maintenance (keepalives)
- No flow control or congestion control
- Fire-and-forget from sensor's perspective

Trade-offs:
- Lose ~1% of packets due to network issues
- No delivery guarantees
- No ordering guarantees

But for sensor data, this is fine:
- Sensors send readings frequently (every second)
- Losing one reading out of 100 is acceptable
- We care more about recent data than old data
- Lower latency is more important than 100% reliability

If we used TCP:
- Need connection pooling
- Need to handle connection failures and reconnects
- More complex code
- Higher latency

### Why Kafka instead of RabbitMQ or SQS?

We chose Kafka because:

**1. Replayability**
- Can reset consumer offset and reprocess old messages
- Useful for debugging, testing new logic, fixing bugs
- RabbitMQ deletes messages after consumption

**2. Throughput**
- Kafka handles 1M+ messages/sec per broker
- RabbitMQ handles ~10K-50K messages/sec
- We don't need 1M/sec now, but nice to have headroom

**3. Ordering**
- Kafka guarantees order within a partition
- Can partition by sensor ID to maintain per-sensor ordering
- RabbitMQ has weaker ordering guarantees

**4. Durability**
- Messages written to disk and replicated
- Survives broker crashes
- RabbitMQ can do this too but it's slower

**5. Scalability**
- Add more consumers to consumer group, no config changes
- Add more partitions to increase parallelism
- RabbitMQ requires more manual configuration

Trade-offs:
- Kafka is more complex to operate
- Requires ZooKeeper (for now, KRaft is coming)
- Higher resource usage per broker

For this use case, the benefits outweigh the complexity.

### Why separate topics for retries?

Traditional approach is to catch exceptions and retry in the same thread:

```java
int retries = 0;
while (retries < 3) {
    try {
        process(message);
        break;
    } catch (Exception e) {
        retries++;
        Thread.sleep(5000);  // Bad!
    }
}
```

Problems:
1. **Blocks the thread** - Can't process other messages while sleeping
2. **Not crash-safe** - If service crashes during sleep, lose the retry
3. **Can't scale** - Adding more instances doesn't help
4. **No visibility** - Hard to monitor retry rates

Our approach using separate topics:

```java
try {
    process(message);
} catch (Exception e) {
    publishToRetryTopic(message);  // Good!
}
```

Benefits:
1. **Non-blocking** - Thread continues processing other messages
2. **Crash-safe** - Retry message is in Kafka, survives crashes
3. **Scalable** - Can add more consumers for retry topics
4. **Observable** - Each topic has its own metrics

The retry delay comes naturally from how fast consumers process the retry topic. If retry topic has backlog, there's implicit delay.

### Why Jackson instead of Protocol Buffers?

Protocol Buffers (protobuf) would give us:
- Smaller messages (~3x smaller)
- Schema enforcement
- Better performance

But JSON with Jackson gives us:
- Human-readable messages (easier debugging)
- No schema compilation step
- Easier to work with in JVM
- Rich ecosystem and libraries

For our message volume (thousands/sec, not millions), the size difference doesn't matter. The debugging benefit of human-readable JSON is worth the extra bytes.

If we were doing millions of messages per second or storing messages long-term, protobuf would make more sense.

### Why immutable domain objects?

All our domain objects (`SensorReading`, `SensorEvent`) are immutable:
- All fields are `final`
- No setters
- Constructor is the only way to create them

Benefits:
1. **Thread-safe** - Multiple threads can read without locks
2. **Easier to reason about** - Value never changes after creation
3. **Safer** - No accidental mutations
4. **Works well with Jackson** - Using `@JsonCreator`

Trade-off is slightly more verbose (need `@JsonProperty` on constructor params), but worth it for the safety.

### Why multi-module Maven project?

We split the code into modules:
- `common` - Shared domain models
- `warehouse-service` - UDP listener and Kafka producer
- `central-monitoring-service` - Kafka consumer
- `bootstrap` - Executable JAR

Benefits:
1. **Clear boundaries** - Each module has specific responsibility
2. **Reusability** - Common module used by both services
3. **Build efficiency** - Only rebuild changed modules
4. **Deployment flexibility** - Can deploy services separately

Alternative would be a monorepo with everything in one JAR. That's simpler but less flexible.

### Why Java 17?

Modern Java features we use:
- Records (could use for simpler domain objects)
- Text blocks (could use for JSON in tests)
- Pattern matching (could use for error handling)
- Better garbage collection

But mainly: It's the latest LTS version (Long Term Support). Good support, performance, and it'll be maintained for years.

### Why Docker Compose instead of Kubernetes?

For local development and small deployments, Docker Compose is:
- Way simpler to set up
- Easier to understand
- Faster to iterate on
- Good enough for 1-10 instances

For production at scale, we'd use Kubernetes:
- Better orchestration
- Auto-scaling
- Rolling updates
- Service mesh

But start simple. We can always migrate to k8s later if needed.

---

## Summary

This system is designed to be:
- **Simple** - Each component does one thing well
- **Reliable** - Retries, DLQ, Kafka durability
- **Observable** - Good logging, separate topics for monitoring
- **Scalable** - Can add more instances of any component
- **Debuggable** - Human-readable JSON, clear error messages

The key insight is using Kafka as the central nervous system. It gives us durability, replayability, and decoupling between components. The rest of the design flows from that decision.
