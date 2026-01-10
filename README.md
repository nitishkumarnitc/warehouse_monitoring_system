```md
# Warehouse Monitoring System

An event-driven, modular warehouse monitoring system that ingests sensor data over UDP, processes events in real time, and triggers alerts based on defined rules.

Designed with clean architecture principles and built using Java 17 and Maven multi-module setup.

---

## High-Level Architecture

```

Sensors
|
|  UDP
v
Warehouse Service
|
|  EventBus
v
Central Monitoring Service
|
v
Alerts / Logs

````

---

## Module Overview

| Module | Description |
|------|------------|
| `common` | Shared domain models, events, and in-memory EventBus |
| `warehouse-service` | UDP listeners, sensor message parsing |
| `central-monitoring-service` | Rule engine and alert processing |
| `bootstrap` | Application entry point to start all services |

---

## Design Principles

- Event-driven architecture
- Loose coupling between modules
- Clear separation of responsibilities
- No cyclic dependencies
- JVM-only dependencies (easy to extend to Kafka / REST)

---

## Technology Stack

- Java 17
- Maven (multi-module)
- Lombok
- SLF4J / Logback
- UDP networking (DatagramSocket)

---

## Prerequisites

- Java 17
- Maven 3.8+

Verify:
```bash
java -version
mvn -version
````

---

## Build the Project

From project root:

```bash
mvn clean install -DskipTests
```

---

## Run the System

### Start the entire system

```bash
cd bootstrap
mvn exec:java -Dexec.mainClass=com.company.bootstrap.SystemLauncher
```

### Start services individually (optional)

Warehouse Service:

```bash
cd warehouse-service
mvn exec:java -Dexec.mainClass=com.company.warehouse.WarehouseApplication
```

Central Monitoring Service:

```bash
cd central-monitoring-service
mvn exec:java -Dexec.mainClass=com.company.monitoring.CentralMonitoringApplication
```

---

## Example Output

```text
Warehouse Service started
Listening on UDP port 3344
Listening on UDP port 3355
ALERT: Temperature exceeded threshold
```

---

## Project Structure

```
warehouse-monitoring-system
├── common
│   ├── model
│   ├── event
│   └── bus
├── warehouse-service
│   ├── udp
│   └── parser
├── central-monitoring-service
│   └── rules
└── bootstrap
```

---

## Future Enhancements

* Replace in-memory EventBus with Kafka
* Add REST APIs for monitoring and alerts
* Persist alerts to database
* Dockerize services
* Metrics and tracing (Micrometer, Prometheus)

---

## Author

Nitish Kumar
Senior Backend /  AI  Engineer

```

