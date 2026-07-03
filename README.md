# MiniGuardium

MiniGuardium is a small database activity monitoring project. It simulates database access events, routes them through RabbitMQ, persists them through an ingestion service, and prepares the event stream for future rule evaluation and alerting.

The project is intentionally built as a simplified Guardium-style system for learning and iteration. The current focus is reliable ingestion and event transport. Rule evaluation, alert APIs, anomaly detection, and a dashboard are planned next steps.

## Current Status

Implemented today:

- A Spring Boot ingestion service that accepts database activity events.
- A Spring Boot traffic simulator that generates synthetic database activity.
- RabbitMQ transport from simulator to ingestion.
- PostgreSQL persistence for raw ingestion records and normalized access events.
- A fallback `POST /events` HTTP ingestion endpoint.
- RabbitMQ publication after `AccessEvent` creation for the future evaluation service.
- A newly initialized Spring Boot evaluation service scaffold.
- Docker Compose wiring for PostgreSQL, RabbitMQ, ingestion, and simulator.

Not implemented yet:

- Rule evaluation and alert creation.
- Alert query APIs.
- Policy configuration APIs.
- Dashboard UI.
- Production authentication and authorization.
- Database migrations.

## High-Level Flow

```text
traffic_simulator
  -> RabbitMQ exchange: guardium.ingestion-events
  -> RabbitMQ queue: guardium.ingestion-events.ingestion-processor
  -> ingestion_processor RawIngestionEventListener
  -> ingestion_events row with PENDING status
  -> async ingestion queue processor
  -> access_events row
  -> RabbitMQ exchange: guardium.access-events
  -> future evaluation_service consumer
```

The ingestion HTTP endpoint remains available for manual events:

```text
client
  -> POST /events
  -> same durable ingestion queue path
```

## Repository Layout

```text
.
|-- AGENTS.md
|-- ARCHITECTURE.md
|-- TASK.md
|-- README.md
|-- compose.yml
|-- docker/
|-- evaluation_service/
|-- ingestion_processor/
`-- traffic_simulator/
```

## Components

### ingestion_processor

`ingestion_processor` is the main ingestion API and worker service.

Responsibilities:

- Consumes raw ingestion messages from RabbitMQ.
- Accepts fallback HTTP ingestion through `POST /events`.
- Validates incoming event DTOs.
- Persists raw ingestion work to `ingestion_events`.
- Processes queued ingestion work asynchronously.
- Normalizes events into `access_events`.
- Publishes processed access-event messages to RabbitMQ for future evaluation.
- Retries failed processing with backoff and retry metadata.

Main runtime dependencies:

- PostgreSQL
- RabbitMQ

Important configuration:

```properties
ingestion.raw-events.exchange=guardium.ingestion-events
ingestion.raw-events.queue=guardium.ingestion-events.ingestion-processor
ingestion.raw-events.routing-key=ingestion-event.created
ingestion.events.exchange=guardium.access-events
ingestion.events.routing-key=access-event.created
spring.jpa.open-in-view=false
```

### traffic_simulator

`traffic_simulator` generates synthetic database access events and publishes them to RabbitMQ.

Responsibilities:

- Generates users, table names, query types, timestamps, source IPs, row counts, and SQL text.
- Publishes raw ingestion messages to `guardium.ingestion-events`.
- Retries transient RabbitMQ publish failures.
- Supports sequential and virtual-thread concurrent publishing.

Default local Compose behavior:

- Enabled.
- Sends 20 events per tick.
- Runs sequentially.
- Publishes once per second unless overridden.

Important configuration:

```properties
traffic-simulator.enabled=false
traffic-simulator.events-per-tick=1
traffic-simulator.tick-rate=PT1S
traffic-simulator.concurrent=false
traffic-simulator.send-retry-attempts=3
traffic-simulator.send-retry-backoff=PT0.25S
traffic-simulator.raw-events.exchange=guardium.ingestion-events
traffic-simulator.raw-events.routing-key=ingestion-event.created
```

### evaluation_service

`evaluation_service` is currently a Spring Boot scaffold. Its intended role is to consume access-event-created messages from RabbitMQ, evaluate rule violations, determine severity, and persist alerts.

Planned first rules:

- Sensitive table access.
- Access outside normal hours.
- `DELETE` without a `WHERE` clause.
- High row count for a single event.

### PostgreSQL

PostgreSQL stores the current application data.

Current tables include:

- `ingestion_events`
- `access_events`
- `users`
- `tables`
- `alerts`

Development schema management currently uses Hibernate `ddl-auto=update`. Migrations should be added before the schema stabilizes.

### RabbitMQ

RabbitMQ is the service-to-service event bus.

Local management UI:

- URL: http://localhost:15672
- Username: `miniguardium`
- Password: `miniguardium_dev_password`

Current exchanges:

- `guardium.ingestion-events`
- `guardium.access-events`

Current raw ingestion queue:

- `guardium.ingestion-events.ingestion-processor`

## Running Locally

### Start PostgreSQL Only

```powershell
docker compose up -d postgres
```

PostgreSQL will be available on `localhost:5432`.

Credentials:

```text
Database: miniguardium
Username: miniguardium
Password: miniguardium_dev_password
```

### Start the Current App Stack

```powershell
docker compose --profile app up -d --build
```

This starts:

- PostgreSQL
- RabbitMQ
- ingestion API
- traffic simulator

The simulator waits for PostgreSQL, RabbitMQ, and the ingestion API to be healthy before publishing events. This avoids losing initial direct-exchange messages before the ingestion-owned queue binding exists.

### Check Service Status

```powershell
docker compose --profile app ps
```

### Follow Logs

```powershell
docker compose --profile app logs -f ingestion-api
docker compose --profile app logs -f traffic-simulator
docker compose --profile app logs -f rabbitmq
```

### Stop the Stack

```powershell
docker compose --profile app down
```

To also remove volumes and clear persisted data:

```powershell
docker compose --profile app down -v
```

## Manual Ingestion API

The ingestion API listens on `localhost:8080` in the Compose app profile.

Example request:

```powershell
$body = @{
  username = 'alice'
  tableName = 'customer_accounts'
  queryType = 'SELECT'
  occurredAt = '2026-07-03T12:00:00Z'
  rowCount = 25
  sourceIp = '10.0.0.25'
  queryText = 'SELECT * FROM customer_accounts WHERE customer_id = 42'
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/events' `
  -ContentType 'application/json' `
  -Body $body
```

Expected response shape:

```json
{
  "ingestionId": 1,
  "status": "PENDING",
  "acceptedAt": "2026-07-03T12:00:00Z"
}
```

The event is processed asynchronously after the ingestion row commits.

## Useful Verification Commands

Check RabbitMQ queue depth:

```powershell
docker compose --profile app exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
```

Check persisted event counts:

```powershell
docker compose --profile app exec -T postgres psql -U miniguardium -d miniguardium -c "select status, count(*) from ingestion_events group by status order by status; select count(*) as access_events from access_events;"
```

Check recent ingestion errors:

```powershell
docker compose --profile app logs --since=5m ingestion-api | Select-String -Pattern "ERROR|Exception|Failed"
```

## Running Tests

On this machine, override `JAVA_HOME` before Maven commands:

```powershell
$env:JAVA_HOME='C:\Program Files\OpenJDK\jdk-25'
```

Run ingestion tests:

```powershell
cd ingestion_processor
.\mvnw.cmd test
```

Run simulator tests:

```powershell
cd traffic_simulator
.\mvnw.cmd test
```

Run evaluation service tests:

```powershell
cd evaluation_service
.\mvnw.cmd test
```

## Current Design Constraints

- The ingestion processor uses an in-memory work queue after persisting `ingestion_events`; it is designed for a single ingestion service instance right now.
- Raw event delivery is durable after RabbitMQ accepts the simulator message, but the simulator does not keep a local durable outbox for unsent events.
- Database rows are not claimed atomically across multiple ingestion instances yet.
- Security is intentionally permissive for local development.
- Hibernate schema updates are used for development; Flyway or Liquibase should be introduced later.
- The evaluation service exists but does not yet consume access-event messages or create alerts.

## More Detail

- See `ARCHITECTURE.md` for the current architecture and implementation notes.
- See `TASK.md` for the remaining roadmap and cleanup items.
- See `AGENTS.md` for project coding conventions and local environment notes.
