# Real-Time Event-Driven Cache Pipeline

This project implements a **Change Data Capture (CDC)** pattern to synchronize a MySQL database with a Redis cache in real-time.

---

## 🏗 Architecture

- **MySQL**: Source of truth database.
- **Debezium**: Captures row‑level changes from MySQL binlogs.
- **Kafka & Zookeeper**: Event streaming and coordination.
- **Spring Boot**: Consumes events and manages Redis invalidation/updates.
- **Redis**: High‑speed read‑through / write‑through cache.

---

## 🛠 Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven

---

## 🚦 Getting Started

### 1. Start Infrastructure

Launch the Docker containers.  
This project uses a **multi‑listener setup for Kafka** so Docker‑internal services talk via `kafka:9093` and your local IDE / tools can connect via `localhost:9092`.

```bash
docker compose up -d
```

### 2. Configure the Debezium Connector

Register the MySQL connector with Kafka Connect. This starts binlog monitoring.

```bash
curl -i -X POST \
  -H "Accept:application/json" \
  -H "Content-Type:application/json" \
  localhost:8083/connectors/ \
  -d '{
  "name": "mysql-orders-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "root",
    "database.server.id": "184002",
    "topic.prefix": "dbserver1",
    "database.include.list": "orders_db",
    "schema.history.internal.kafka.bootstrap.servers": "kafka:9093",
    "schema.history.internal.kafka.topic": "schema-history.orders",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false"
  }
}'
```

### 3. Run the Spring Boot Application

- Ensure `application.properties` points to:
    - Kafka: `localhost:9092`
    - Redis: `localhost:6379`
- **Crucial**: Ensure no local Redis service is running on your host (e.g., stop it with `brew services stop redis` on macOS).

---

## 🔍 Useful Commands for Developers

### 📝 Monitoring Logs

**Check Debezium / Kafka Connect logs:**

```bash
docker logs -f kafka-connect
```

**Monitor Kafka events (CLI consumer):**

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic dbserver1.orders_db.orders \
  --from-beginning \
  --property print.key=true \
  --property print.value=true
```

### 🛠 Troubleshooting Connections

**Check port ownership (macOS):**

```bash
lsof -i :6379   # Redis
lsof -i :9092   # Kafka external
```

**Verify Redis content:**

```bash
docker exec -it redis redis-cli
127.0.0.1:6379> KEYS *
127.0.0.1:6379> GET ORDER_1
```

### 🔄 Connector Management

**Check connector status:**

```bash
curl -s localhost:8083/connectors/mysql-orders-connector/status | jq
```

**Delete connector (to restart/update):**

```bash
curl -X DELETE localhost:8083/connectors/mysql-orders-connector
```

---

## 💡 Implementation Notes

- **Tombstone Handling**:  
  Debezium sends a null payload after a delete to allow Kafka log compaction.  
  The Spring Boot listener checks `if (event == null)` and parses the Kafka key to identify the ID to remove from Redis.

- **Network mapping**:
    - **Internal (Docker‑to‑Docker)**: `kafka:9093`
    - **External (IDE‑to‑Docker)**: `localhost:9092`