# Real-Time Event-Driven Cache & Search Pipeline

This project implements a **Change Data Capture (CDC)** pattern to synchronize a MySQL database with **both** a Redis cache (for fast lookups) and Elasticsearch (for complex searching) in real-time.

---

## 🏗 Architecture

- **MySQL**: Source of truth database (write side / authoritative store).
- **Debezium**: Captures row‑level changes from MySQL binlogs.
- **Kafka & Zookeeper**: Event streaming and coordination backbone (central nervous system).
- **Kafka Connect (CDC Source)**: MySQL → Kafka connector, configured with `transforms.unwrap` to emit clean, flattened events.
- **Kafka Connect (Elasticsearch Sink)**: Kafka → Elasticsearch connector, performing **idempotent upserts** using MySQL primary keys as Elasticsearch `_id`.
- **Spring Boot**: Consumes CDC events for **Redis cache invalidation / updates** (e.g., `ORDER_ID` keys).
- **Redis**: High‑speed **read‑through / write‑through cache** for low‑latency queries.
- **Elasticsearch**: Distributed search engine for **rich queries, aggregations, and full‑text search** (read side index).

This forms a **CQRS‑like** pattern where:

- Writes go to **MySQL** (source of truth).
- Reads are split:
  - Simple key‑by‑ID via **Redis**.
  - Search / filter‑based queries via **Elasticsearch**.

---

## 🛠 Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven
- Memory: Recommended **8GB+** (running Kafka, MySQL, Redis, and Elasticsearch simultaneously).

---

## 🚦 Getting Started

### 1. Start Infrastructure

Launch the Docker containers.  
The stack uses a **multi‑listener Kafka setup**: Docker‑internal services talk via `kafka:9093`, while your IDE / tools connect via `localhost:9092`.

```bash
docker compose up -d
```

Wait a few seconds for Elasticsearch, Kafka Connect, and MySQL to stabilize.

### 2. Configure Debezium (MySQL Source Connector)

Starts monitoring MySQL binlogs and streaming changes to Kafka.

```bash
curl -i -X POST \
  -H "Accept:application/json" \
  -H "Content-Type:application/json" \
  localhost:8083/connectors/ \
  -d '{
  "name": "mysql-orders-source",
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
    "transforms.unwrap.add.fields": "op,table",
    "transforms.unwrap.drop.tombstones": "false"
  }
}'
```

You can monitor topic creation and records arriving with:

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic dbserver1.orders_db.orders \
  --from-beginning \
  --property print.key=true \
  --property print.value=true
```

### 3. Configure Elasticsearch (Sink Connector)

Pipes data from Kafka to Elasticsearch. Uses Single Message Transforms (SMTs) so MySQL primary keys map to Elasticsearch `_id`, enabling idempotent `upsert` semantics.

```bash
curl -i -X POST \
  -H "Content-Type:application/json" \
  localhost:8083/connectors/ \
  -d '{
  "name": "elastic-sink-orders",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "topics": "dbserver1.orders_db.orders",
    "connection.url": "http://elasticsearch:9200",
    "type.name": "_doc",
    "key.ignore": "false",
    "schema.ignore": "true",
    "transforms": "unwrap,extractId",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.extractId.type": "org.apache.kafka.connect.transforms.ExtractField$Key",
    "transforms.extractId.field": "id",
    "behavior.on.null.values": "DELETE",
    "write.method": "upsert"
  }
}'
```

### 4. Run the Spring Boot Application

- Ensure `application.properties` points to:
  - Kafka: `localhost:9092`
  - Redis: `localhost:6379`
- **Crucial**: Ensure no local Redis service is running on your host (e.g., on macOS):
  ```bash
  brew services stop redis
  ```

Spring Boot listens to `dbserver1.orders_db.orders` and:

- Updates or invalidates Redis keys (e.g., `ORDER_<ID>`) on `CREATE` / `UPDATE` / `DELETE`.
- Handles Debezium tombstone events (`payload: null`) by removing the corresponding key from Redis.

---

## 🔍 Useful Commands for Developers

### 📊 Elasticsearch Searching

Verify documents have been indexed:

```bash
# List indices
curl -s "localhost:9200/_cat/indices?v"

# Search for all paid orders
curl -X GET \
  "localhost:9200/dbserver1.orders_db.orders/_search?q=status:PAID&pretty"

# Or more structured query
curl -X GET \
  "localhost:9200/dbserver1.orders_db.orders/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "term": { "status": "PAID" }
    }
  }'
```

### 📝 Monitoring & Logs

**Check Debezium / Kafka Connect logs:**

```bash
docker logs -f kafka-connect
```

**Check connector status:**

```bash
curl -s localhost:8083/connectors/mysql-orders-source/status | jq
curl -s localhost:8083/connectors/elastic-sink-orders/status | jq
```

**List Kafka topics:**

```bash
docker exec -it kafka \
  kafka-topics \
  --list \
  --bootstrap-server localhost:9092
```

### 🛠 Troubleshooting Connections

**Check port ownership (macOS):**

```bash
lsof -i :6379   # Redis
lsof -i :9092   # Kafka external
lsof -i :9200   # Elasticsearch
```

**Verify Redis content:**

```bash
docker exec -it redis redis-cli
127.0.0.1:6379> KEYS *
127.0.0.1:6379> GET ORDER_1
```

### 🔄 Connector Management

**Delete a connector (to reset / update config):**

```bash
curl -X DELETE localhost:8083/connectors/mysql-orders-source
curl -X DELETE localhost:8083/connectors/elastic-sink-orders
```

When you recreate them, Kafka Connect picks up the latest configuration from the `connect-configs` topic.

---

## 💡 Implementation Notes

- **Idempotency in Elasticsearch**  
  By using `transforms`:
  - `unwrap` → `after` record is the document body.
  - `extractId` → `key.id` becomes the Elasticsearch `_id`.  
    This ensures **each MySQL row has a single, unique document** in Elasticsearch, even after updates or connector restarts.

- **Tombstone Handling in Redis**  
  When Debezium produces a **null payload** after a delete:
  - The key is still present (e.g., `{"id": 7}`).
  - Spring Boot checks `if (event == null)` and deletes the corresponding Redis key based on the extracted ID.

- **Distributed Configuration Source of Truth**
  - Kafka Connect stores connector configs in `connect-configs`, offsets in `connect-offsets`, and status in `connect-status`.
  - Even if the `kafka-connect` container is replaced, new workers resume from the same point.

- **Network Mapping**
  - **Internal (Docker‑to‑Docker)**:
    - Kafka: `kafka:9093`
    - Elasticsearch: `elasticsearch:9200`
  - **External (IDE‑to‑Docker)**:
    - Kafka: `localhost:9092`
    - Elasticsearch: `localhost:9200`

- **Full Reset Strategy**  
  To start clean:
  1. Delete connectors:
     ```bash
     curl -X DELETE localhost:8083/connectors/mysql-orders-source
     curl -X DELETE localhost:8083/connectors/elastic-sink-orders
     ```
  2. Remove Elasticsearch index:
     ```bash
     curl -X DELETE localhost:9200/dbserver1.orders_db.orders
     ```
  3. Optionally delete Kafka topics (after `docker compose down`).

---

## 🚀 Next Steps

- Connect Spring Boot to Elasticsearch using `RestHighLevelClient` or `Spring Data Elasticsearch` to expose search endpoints.
- Add a simple REST controller that:
  - Reads `ORDER_<ID>` from Redis.
  - Searches orders by `status`, `user_id`, `created_at` via Elasticsearch.
- Experiment with enriching events (e.g., `user_name`, `product_details`) in downstream consumers or another Kafka Connect sink.

Having both **Redis** and **Elasticsearch** synced from the same CDC stream enables a powerful, scalable, and low‑latency read‑side for your application.