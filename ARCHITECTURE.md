# ARCHITECTURE.md

MiniGuardium is a simplified real-time database activity monitor. The current implementation focuses on reliable event ingestion. The broader plan adds rule-based alerting, anomaly detection, policy APIs, a traffic simulator, and a dashboard.

## System Context

Planned system flow:

```text
[DB Traffic Simulator] -> [Ingestion API] -> [Event Store]
                                |
                                v
                         [Rule Evaluation]
                                |
                                v
                           [Alerts API]
                                |
                                v
                            [Dashboard]

Future stretch:
[Event Store or Stream] -> [Anomaly Detector] -> [Alerts]
```

Current implemented flow:

```text
POST /events
    -> EventIngestionController
    -> EventIngestionService
    -> ingestion_events row with PENDING status
    -> Spring application event after transaction commit
    -> IngestionQueueProcessor in-memory priority queue
    -> access_events row
    -> ingestion_events status updated to PROCESSED or FAILED
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
`-- ingestion_processor/
    |-- Dockerfile
    |-- pom.xml
    `-- src/
```

`ingestion_processor` is currently the only implemented application service. Compose also reserves future profiles for `analytics_engine`, `traffic_simulator`, `dashboard`, and Redis-backed streaming.

## Ingestion Service

The ingestion service is a Java/Spring Boot application with these responsibilities:

- accept event payloads through `POST /events`,
- validate required event fields,
- persist ingestion requests as queue records,
- return a small DTO instead of exposing JPA entities,
- process queued records asynchronously into normalized access events,
- retry transient processing failures with exponential backoff and jitter,
- log service and processor activity with ingestion metadata.

### API Layer

`EventIngestionController` exposes `POST /events`.

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
- Marks the ingestion row `PROCESSED` on success.
- Marks the row `FAILED` on runtime failure and schedules `nextAttemptAt` unless max retries are reached.

Current retry settings:
- max retries: `5`,
- base backoff: `5 seconds`,
- max backoff: `5 minutes`,
- jitter: random additional delay up to half of the capped delay.

## Data Model

Current entities:

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

The service uses Log4j2 through `log4j2-spring.xml`. Logs include MDC-style fields:

- `requestId`,
- `ingestionEventId`.

These fields allow a single ingestion request to be traced from acceptance through queued processing and retries.

## Security

`SecurityConfig` currently disables CSRF and permits all requests. This keeps local ingestion and simulation simple while the event pipeline is being built.

Planned security work:
- API key or basic auth for application endpoints,
- tests for unauthenticated and authenticated requests,
- health endpoint access suitable for Compose checks.

## Container Architecture

`compose.yml` currently defines:

- `postgres`: PostgreSQL 17 with health checks and a persistent volume.
- `ingestion-api`: Spring Boot service under the `app` profile.
- `anomaly-detector`: future profile placeholder.
- `traffic-simulator`: future profile placeholder.
- `dashboard`: future profile placeholder.
- `redis`: streaming profile placeholder.

The ingestion Dockerfile uses a Maven build stage and an Eclipse Temurin 25 JRE Jammy runtime stage.

## Testing Architecture

The test suite covers:

- controller validation and DTO response shape,
- unauthenticated health access,
- service enqueue behavior and tracing metadata,
- queue processor success, retries, skips, priority, duplicate suppression, and nested queue drain behavior,
- repository query behavior against H2,
- JPA lifecycle timestamps,
- DTO mapping,
- application context and `main` delegation.

Tests are written with AssertJ for readability.

## Architectural Constraints and Future Decisions

Current single-instance assumption:
- The queue is in memory.
- The dedupe set is in memory.
- Rows are not claimed atomically for multi-instance processing.

If the ingestion service runs with more than one instance, queue processing must change. Viable options:

- atomic database row claiming,
- PostgreSQL `FOR UPDATE SKIP LOCKED`,
- Redis Streams,
- Kafka.

The planned anomaly detector can initially be implemented inside the Java service for speed, then moved to a Python service or stream consumer if the project reaches the Week 4 stretch architecture.
