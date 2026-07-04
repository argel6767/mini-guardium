# ARCHITECTURE.md

MiniGuardium is a simplified real-time database activity monitor. The current implementation has three Java/Spring Boot services: a reliable ingestion service, a traffic simulator that produces synthetic database activity, and an evaluation service that creates and exposes alerts. The broader plan adds configurable policies, anomaly detection, production security, and a dashboard.

## System Context

Current implemented flow:

```text
[Traffic Simulator]
    -> RabbitMQ exchange guardium.ingestion-events
    -> queue guardium.ingestion-events.ingestion-processor
    -> RawIngestionEventListener
    -> EventIngestionService
    -> ingestion_events row with PENDING status
    -> Spring application event after transaction commit
    -> IngestionQueueProcessor in-memory priority queue
    -> access_events row
    -> RabbitMQ exchange guardium.access-events
    -> queue guardium.access-events.evaluation-service
    -> AccessEventCreatedListener
    -> AccessEventEvaluationService
    -> alerts row when evaluation produces a severity
    -> Alert REST and SSE APIs

Manual/API fallback:
[Client]
    -> POST /events
    -> EventIngestionController
    -> same EventIngestionService path
```

Future visibility and analytics flow:

```text
[Alert REST/SSE APIs]
    -> [Dashboard]

[Access Events or Alert Stream]
    -> [Rolling Baseline / Anomaly Detector]
    -> [Alerts]
```

## Repository Layout

```text
.
|-- AGENTS.md
|-- ARCHITECTURE.md
|-- TASK.md
|-- README.md
|-- compose.yml
|-- project-plan-doc.md
|-- docker/
|-- evaluation_service/
|   |-- Dockerfile
|   |-- pom.xml
|   `-- src/
|-- ingestion_processor/
|   |-- Dockerfile
|   |-- pom.xml
|   `-- src/
`-- traffic_simulator/
    |-- Dockerfile
    |-- pom.xml
    `-- src/
```

Implemented application services:

- `ingestion_processor`: consumes raw RabbitMQ ingestion events, accepts fallback HTTP events, persists queue records, and processes database activity into normalized access events.
- `traffic_simulator`: generates synthetic ingestion payloads and publishes them to RabbitMQ.
- `evaluation_service`: consumes processed access-event messages, evaluates risk, persists alerts, and serves alert data through REST and SSE endpoints.

Compose still contains future placeholders for `analytics_engine`, `dashboard`, and Redis-backed streaming.

## Ingestion Service

The ingestion service is a Java/Spring Boot application with these responsibilities:

- consume raw ingestion event payloads from RabbitMQ,
- accept fallback event payloads through `POST /events`,
- validate required event fields,
- persist ingestion requests as queue records,
- return a small DTO instead of exposing JPA entities,
- process queued records asynchronously into normalized access events,
- retry transient processing failures with exponential backoff and jitter,
- publish access-event-created messages to RabbitMQ after the `AccessEvent` row commits,
- log service and processor activity with ingestion metadata.

### API Layer

`EventIngestionController` exposes `POST /events`. This endpoint remains supported for manual ingestion and API clients even though the traffic simulator publishes through RabbitMQ.

Request DTO: `IngestEventRequest`

Fields:

- `username` - required, non-blank.
- `tableName` - required, non-blank.
- `queryType` - required enum.
- `occurredAt` - optional event timestamp.
- `rowCount` - must be zero or positive.
- `sourceIp` - required, non-blank.
- `queryText` - optional query text.

Response DTO: `IngestEventResponse`

Fields:

- `ingestionId`,
- `status`,
- `acceptedAt`.

The response intentionally hides database internals and event details such as query text, user entity, table entity, retry metadata, and generated persistence fields.

### Service Layer

`RawIngestionEventListener` consumes messages from RabbitMQ, validates them, maps them into `IngestEventRequest`, and delegates to `EventIngestionService`. Listener exceptions are not swallowed; Spring AMQP listener retry handles consumer failures.

`EventIngestionService` creates an `IngestionEvent` from the request DTO and saves it with `PENDING` status. After saving, it publishes `IngestionQueuedEvent` with the ingestion id. Logging uses Log4j2 `CloseableThreadContext` to attach:

- `requestId=ingestion-{id}`,
- `ingestionEventId={id}`.

### Queue Processor

`IngestionQueueProcessor` handles both immediate and scheduled queue work.

Immediate path:

- `@TransactionalEventListener(phase = AFTER_COMMIT)` waits until the ingestion row commits.
- `@Async` lets the client return without waiting for access-event processing.
- The ingestion id is added to a local `PriorityBlockingQueue`.

Retry sweep path:

- `@Scheduled` periodically fetches pending rows and retryable failed rows.
- `PENDING` work has higher priority than `FAILED` retry work.
- A `queuedIds` set reduces duplicate queue entries inside the single app instance.

Processing path:

- Loads the `IngestionEvent` in a transaction.
- Skips missing, already processed, max-retry, or not-yet-due rows.
- Marks eligible rows `PROCESSING` and records `lastAttemptAt`.
- Finds or creates `DatabaseUser` and `DatabaseTable` rows.
- Saves an `AccessEvent`.
- Registers an after-commit callback that publishes the access-event-created message.
- Marks the ingestion row `PROCESSED` on success.
- Marks the row `FAILED` on runtime failure and schedules `nextAttemptAt` unless max retries are reached.

Publishing after commit prevents the evaluation service from consuming an access-event id before the database row is visible.

Current retry settings:

- max retries: `5`,
- base backoff: `5 seconds`,
- max backoff: `5 minutes`,
- jitter: random additional delay up to half of the capped delay.

## Evaluation Service

The evaluation service is a Java/Spring Boot application with these responsibilities:

- declare the durable access-event evaluation queue and binding,
- consume access-event-created messages from RabbitMQ,
- load the corresponding `AccessEvent` with its user and table,
- evaluate hardcoded rule signals,
- convert the total score into `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`,
- persist an `Alert` linked to the access event when the score is alert-worthy,
- publish an in-process `AlertCreatedEvent`,
- serve alert data to dashboard clients.

### Consumer Path

`AccessEventCreatedListener` consumes from:

```text
queue: guardium.access-events.evaluation-service
exchange: guardium.access-events
routing key: access-event.created
```

The evaluation service is part of the Compose `app` profile, has its own health check, and the simulator waits for it before generating traffic. This startup ordering keeps early direct-exchange messages from being dropped before the evaluation queue exists.

### Rule Evaluation

`AccessEventEvaluationService` and `AccessEventEvaluationUtils` currently use hardcoded rules and role/table maps.

Current signals include:

- sensitive table access,
- access outside the role's expected hours,
- highly suspicious access hours,
- `DELETE` without a `WHERE` clause,
- unusually high row count,
- role/table permission mismatch.

The evaluation code handles the real simulator and ingestion values, such as usernames like `alice` and table names like `customer_accounts`, instead of expecting enum names from the message payload.

`getTablePermission()` maps all current query types generated by the simulator:

- `SELECT` -> read,
- `INSERT` and `CREATE` -> write,
- `UPDATE` and `ALTER` -> update,
- `DELETE` and `DROP` -> delete,
- `OTHER` -> read fallback.

### Alert API

`AlertController` exposes:

```text
GET /alerts
GET /alerts/summary
```

`GET /alerts` supports pagination and optional filters for:

- severity,
- rule name,
- username,
- table name,
- created-from timestamp,
- created-to timestamp.

The service uses DTOs rather than exposing JPA entities, and uses dynamic JPA specifications so null filters are omitted instead of passed into fragile optional JPQL predicates.

`GET /alerts/summary` returns total counts and grouped counts by severity, rule name, user, and table.

### Alert SSE Streams

`AlertStreamController` exposes:

```text
GET /alerts/stream/severity
GET /alerts/stream/batches
GET /alerts/stream/rates
```

Stream behavior:

- `/severity` emits a small event when an alert is created, intended for low-cost live badges or toast indicators.
- `/batches` emits recent alerts in batches, reducing client update pressure compared with sending every full alert individually.
- `/rates` emits rolling alert-rate snapshots for line graphs, including overall rates and rates grouped by severity/rule type.

## Traffic Simulator

The traffic simulator is a Java/Spring Boot service that generates synthetic database activity and publishes it to RabbitMQ.

Current behavior:

- generates ingestion-compatible DTOs with username, table name, query type, event timestamp, row count, source IP, and SQL text,
- wraps each generated event in a raw ingestion RabbitMQ message with a generated `simulatedEventId`,
- weights generated query types toward common query types while still producing DDL, delete, and fallback cases,
- generates some `DELETE` statements without `WHERE` clauses to exercise alert rules,
- publishes events through Spring AMQP `RabbitTemplate`,
- logs successful publishes with simulated event id and event metadata,
- retries transient publish failures for the same generated event before giving up,
- generates event timestamps inside the last 14 days and never in the future,
- uses role-aware time-window probabilities to avoid every event looking suspicious.

Simulator configuration:

```properties
traffic-simulator.enabled=false
traffic-simulator.raw-events.exchange=guardium.ingestion-events
traffic-simulator.raw-events.routing-key=ingestion-event.created
traffic-simulator.events-per-tick=1
traffic-simulator.tick-rate=PT1S
traffic-simulator.concurrent=false
traffic-simulator.send-retry-attempts=3
traffic-simulator.send-retry-backoff=PT0.25S
```

Runtime modes:

- sequential mode is the default and sends the configured batch one request at a time,
- concurrent mode uses virtual threads to send each event in a batch concurrently,
- Compose currently runs sequential mode with `20` events per tick.

## Data Model

Current entities shared in shape across ingestion and evaluation service packages:

- `IngestionEvent`: durable queue entry containing original event payload, status, retry metadata, and timestamps.
- `DatabaseUser`: unique database user name.
- `DatabaseTable`: unique table name plus `sensitive` flag.
- `AccessEvent`: normalized processed database access event.
- `Alert`: alert record linked to an access event.

Current enums:

- `QueryType`: event query type.
- `IngestionStatus`: queue processing state.
- `AlertSeverity`: alert severity.

Indexes currently defined on `ingestion_events`:

- `status`,
- `status,retry_count,next_attempt_at,created_at` for retry scanning.

## Persistence

Runtime persistence is PostgreSQL through Compose. Tests use H2 in PostgreSQL compatibility mode through `application-test.properties`.

Development schema management currently uses:

```properties
spring.jpa.hibernate.ddl-auto=update
```

This is acceptable for early iteration, but migrations should be introduced before the schema stabilizes or multiple services depend on it.

## Logging and Tracing

The ingestion service uses Log4j2 through `log4j2-spring.xml`. Logs include MDC-style fields:

- `requestId`,
- `ingestionEventId`.

These fields allow a single ingestion request to be traced from acceptance through queued processing and retries.

The traffic simulator uses standard Spring logging for publish successes, publish retries, exhausted publish failures, and interrupted retry waits.

The evaluation service logs access-event evaluation, alert creation, and stream activity through standard Spring logging.

## Security

`SecurityConfig` in the services currently disables CSRF and permits all requests. This keeps local ingestion, simulation, and dashboard API exploration simple while the event pipeline is being built.

Planned security work:

- API key or basic auth for application endpoints,
- tests for unauthenticated and authenticated requests,
- health endpoint access suitable for Compose checks.

## Container Architecture

`compose.yml` currently defines:

- `postgres`: PostgreSQL 17 with health checks and a persistent volume.
- `rabbitmq`: RabbitMQ with the management UI under the `app` profile.
- `ingestion-api`: Spring Boot ingestion service under the `app` profile.
- `evaluation-service`: Spring Boot evaluation and alert API service under the `app` profile.
- `traffic-simulator`: Spring Boot simulator service under the `app` profile.
- `anomaly-detector`: future profile placeholder.
- `dashboard`: future profile placeholder.
- `redis`: streaming profile placeholder.

Compose startup behavior:

- `ingestion-api` waits for PostgreSQL and RabbitMQ to become healthy,
- `evaluation-service` waits for PostgreSQL and RabbitMQ to become healthy,
- both Java services expose TCP health checks for port `8080`,
- `traffic-simulator` waits for PostgreSQL, RabbitMQ, ingestion, and evaluation to become healthy before publishing,
- durable RabbitMQ queues allow short service restarts after queue declaration,
- simulator publish retries handle short RabbitMQ connection failures after startup.

The ingestion API is exposed as `localhost:8080`; the evaluation service is exposed as `localhost:8081`.

Implemented service Dockerfiles use a Maven build stage and an Eclipse Temurin 25 JRE Jammy runtime stage.

## Testing Architecture

The ingestion test suite covers:

- controller validation and DTO response shape,
- unauthenticated health access,
- service enqueue behavior and tracing metadata,
- raw RabbitMQ listener mapping and invalid-message failure behavior,
- queue processor success, retries, skips, priority, duplicate suppression, nested queue drain behavior, and after-commit publishing,
- repository query behavior against H2,
- JPA lifecycle timestamps,
- DTO mapping,
- application context and `main` delegation.

The traffic simulator test suite covers:

- DTO validation compatibility with the ingestion API,
- generated payload shape,
- RabbitMQ publisher payloads,
- sender publish success, transient retry, exhausted retry, and interrupted retry behavior,
- sequential and concurrent batch sending,
- scheduler enabled/disabled behavior,
- service selection between sequential and concurrent modes,
- role-aware timestamp generation,
- application context startup.

The evaluation service test suite covers:

- RabbitMQ configuration and listener behavior,
- access-event evaluation rule paths and severity mapping,
- alert persistence,
- alert dashboard filtering and summary behavior,
- alert DTO mapping,
- REST controller behavior,
- SSE stream service behavior,
- application context startup.

Tests are written with AssertJ and Mockito for readability.

## Architectural Constraints and Future Decisions

Current single-instance ingestion assumption:

- The queue is in memory.
- The dedupe set is in memory.
- Rows are not claimed atomically for multi-instance processing.

If the ingestion service runs with more than one instance, queue processing must change. Viable options:

- atomic database row claiming,
- PostgreSQL `FOR UPDATE SKIP LOCKED`,
- Redis Streams,
- Kafka.

Simulator delivery is best-effort until RabbitMQ accepts the message. It retries short publish failures, but it does not maintain a durable local outbox before broker acceptance. This is acceptable for synthetic load generation, not for authoritative event capture.

The planned anomaly detector can initially be implemented inside the Java service for speed, then moved to a Python service or stream consumer if the project reaches the Week 4 stretch architecture.
