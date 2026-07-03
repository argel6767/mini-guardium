# ARCHITECTURE.md

MiniGuardium is a simplified real-time database activity monitor. The current implementation has three Java/Spring Boot services in the repository: a reliable ingestion service, a traffic simulator that produces synthetic database activity, and a newly initialized evaluation service. The broader plan adds rule-based alerting, anomaly detection, policy APIs, and a dashboard.

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
    -> ingestion_events status updated to PROCESSED or FAILED

Manual/API fallback:
[Client]
    -> POST /events
    -> EventIngestionController
    -> same EventIngestionService path
```

Planned alerting and visibility flow:

```text
[Access Event]
    -> Rule Evaluation
    -> alerts row
    -> Alerts API
    -> Dashboard

Future stretch:
[Event Store or Stream] -> [Anomaly Detector] -> [Alerts]
```

## Repository Layout

```text
.
|-- AGENTS.md
|-- ARCHITECTURE.md
|-- TASK.md
|-- compose.yml
|-- project-plan-doc.md
|-- docker/
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

- `ingestion_processor`: consumes raw RabbitMQ ingestion events, accepts fallback HTTP events, persists, queues, and processes database activity events.
- `traffic_simulator`: generates synthetic ingestion payloads and publishes them to RabbitMQ.
- `evaluation_service`: initialized Spring Boot service that will consume processed access-event messages in a later step.

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
- log service and processor activity with ingestion metadata.

### API Layer

`EventIngestionController` exposes `POST /events`. This endpoint remains supported for manual ingestion and API clients even though the traffic simulator now publishes through RabbitMQ.

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
- Publishes an access-event-created message to RabbitMQ for evaluation consumers.
- Marks the ingestion row `PROCESSED` on success.
- Marks the row `FAILED` on runtime failure and schedules `nextAttemptAt` unless max retries are reached.

Current retry settings:
- max retries: `5`,
- base backoff: `5 seconds`,
- max backoff: `5 minutes`,
- jitter: random additional delay up to half of the capped delay.

## Traffic Simulator

The traffic simulator is a Java/Spring Boot service that generates synthetic database activity and publishes it to RabbitMQ.

Current behavior:

- generates ingestion-compatible DTOs with username, table name, query type, event timestamp, row count, source IP, and SQL text,
- wraps each generated event in a raw ingestion RabbitMQ message with a generated `simulatedEventId`,
- weights generated query types toward `SELECT`, with occasional `INSERT`, `UPDATE`, and `DELETE`,
- generates some `DELETE` statements without `WHERE` clauses to support future rule-alert testing,
- publishes events through Spring AMQP `RabbitTemplate`,
- logs successful publishes with simulated event id and event metadata,
- catches and logs `AmqpException` failures so one publish failure does not stop the scheduler,
- retries transient publish failures for the same generated event before giving up.

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

Current ingestion entities:

- `IngestionEvent`: durable queue entry containing original event payload, status, retry metadata, and timestamps.
- `DatabaseUser`: unique database user name.
- `DatabaseTable`: unique table name plus `sensitive` flag.
- `AccessEvent`: normalized processed database access event.
- `Alert`: planned alert record linked to an access event.

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

## Security

`SecurityConfig` in the ingestion service currently disables CSRF and permits all requests. This keeps local ingestion and simulation simple while the event pipeline is being built.

The simulator includes Spring Security on the classpath but does not expose application APIs beyond the default actuator endpoint behavior.

Planned security work:
- API key or basic auth for application endpoints,
- tests for unauthenticated and authenticated requests,
- health endpoint access suitable for Compose checks.

## Container Architecture

`compose.yml` currently defines:

- `postgres`: PostgreSQL 17 with health checks and a persistent volume.
- `rabbitmq`: RabbitMQ with the management UI under the `app` profile.
- `ingestion-api`: Spring Boot ingestion service under the `app` profile.
- `traffic-simulator`: Spring Boot simulator service under the `app` profile.
- `anomaly-detector`: future profile placeholder.
- `dashboard`: future profile placeholder.
- `redis`: streaming profile placeholder.

Compose startup behavior:

- `ingestion-api` waits for PostgreSQL and RabbitMQ to become healthy,
- `ingestion-api` exposes a TCP health check for port `8080`,
- `traffic-simulator` waits for RabbitMQ to become healthy before starting,
- durable RabbitMQ queues allow simulator publishes to survive short ingestion restarts,
- simulator publish retries handle short RabbitMQ connection failures after startup.

Both implemented service Dockerfiles use a Maven build stage and an Eclipse Temurin 25 JRE Jammy runtime stage.

## Testing Architecture

The ingestion test suite covers:

- controller validation and DTO response shape,
- unauthenticated health access,
- service enqueue behavior and tracing metadata,
- raw RabbitMQ listener mapping and invalid-message failure behavior,
- queue processor success, retries, skips, priority, duplicate suppression, and nested queue drain behavior,
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
