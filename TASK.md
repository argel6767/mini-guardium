# TASK.md

This task list reflects the current state of the ingestion service, traffic simulator, evaluation service initialization, Compose setup, and remaining work from `project-plan-doc.md`.

## Current Baseline

Completed:
- Spring Boot ingestion service exists under `ingestion_processor`.
- `POST /events` accepts validated event DTOs and returns a minimal ingestion DTO with `202 Accepted`.
- Ingestion is asynchronous from the client's perspective: accepted events are persisted to `ingestion_events` as queue entries, then processed into `access_events` by an async worker.
- Queue processing supports `PENDING`, `PROCESSING`, `PROCESSED`, and `FAILED` states.
- Failed ingestion events use retry metadata: `retryCount`, `lastAttemptAt`, and `nextAttemptAt` with exponential backoff and jitter.
- Pending work and retry scans are indexed for the current single-instance simulation design.
- Log4j2 is configured with `requestId` and `ingestionEventId` tracing metadata.
- H2-backed tests cover the ingestion endpoint, service behavior, processor behavior, repository queries, lifecycle callbacks, DTO mapping, and app startup.
- Dockerfile and Compose app profile can run the ingestion service with PostgreSQL.
- Spring Boot traffic simulator exists under `traffic_simulator`.
- The simulator generates ingestion-compatible events with username, table name, query type, timestamp, row count, source IP, and query text.
- The simulator supports configurable RabbitMQ exchange/routing key, tick rate, events per tick, sequential mode, and virtual-thread concurrent mode.
- Simulator publish logic retries transient RabbitMQ transport failures before logging a final failure.
- Compose app profile runs PostgreSQL, RabbitMQ, ingestion API, and traffic simulator together.
- Compose waits for PostgreSQL and RabbitMQ health before starting ingestion, and the simulator waits for RabbitMQ health before publishing.
- Traffic simulator tests cover payload generation, RabbitMQ publishing, scheduler behavior, service selection, sequential/concurrent sending, retry behavior, and app startup.
- RabbitMQ is part of the Compose app profile and is used for service-to-service event transport.
- The simulator now publishes raw ingestion events to RabbitMQ instead of calling ingestion over HTTP.
- The ingestion service consumes raw ingestion events from RabbitMQ and maps them into the existing durable ingestion queue path.
- `POST /events` remains available as a fallback/manual ingestion API.
- Ingestion publishes access-event-created messages to RabbitMQ after `AccessEvent` creation for the future evaluation service.
- Spring AMQP listener retry is configured for raw ingestion consumer failures.
- The evaluation service has been initialized as a Spring Boot service and will consume processed access events in a future step.

Known constraints:
- The ingestion in-memory processing queue is safe for the current single-ingestion-instance simulation, but it is not a distributed worker queue.
- Raw ingestion delivery is brokered through RabbitMQ, but queued database rows are still not claimed atomically across multiple app instances.
- Simulator delivery is durable only after RabbitMQ accepts a message; it does not persist unsent events locally.
- Security currently permits all ingestion requests; this is intentional for early local ingestion work but must change before exposing the API.
- Hibernate `ddl-auto=update` is being used for development instead of a migration tool.
- Documentation for local run commands is still light; `AGENTS.md` contains the local `JAVA_HOME` override note for Maven commands on this machine.

## Next Tasks

### Week 1 Completion: Rule-Based Alerts

- Build out the initialized evaluation service so it consumes access-event-created messages from RabbitMQ after an `AccessEvent` is created.
- Persist alerts to the existing `alerts` table.
- Implement initial hardcoded rules:
  - access to a sensitive table,
  - access outside normal hours,
  - `DELETE` query without a `WHERE` clause,
  - unusually high row count for a single event.
- Add tests for each rule and for the no-alert path.
- Keep alert generation in the evaluation service consumer rather than inside `IngestionQueueProcessor`, unless a later design change requires synchronous evaluation.

### API Visibility

- Add `GET /alerts` with filtering by severity, rule name, time range, table, and user.
- Add DTOs for alert responses; do not return JPA entities directly.
- Add pagination and stable ordering for alert list responses.
- Add tests for filtering, DTO shape, and hidden fields.
- Add a simple `GET /events/{ingestionId}` endpoint to check queue status if clients need ingestion tracking.

### Policies

- Add a policy model for configurable sensitive tables and thresholds.
- Add `/policies` endpoints for creating, updating, listing, and deleting policies.
- Use DTOs and validation on all policy requests.
- Apply policies during rule evaluation.
- Add tests for policy validation and rule behavior with configured policies.

### Traffic Simulator Follow-Up

- Document how to run simulator, RabbitMQ, and ingestion through Compose.
- Add an end-to-end Compose smoke test or manual verification script for simulator-to-RabbitMQ-to-ingestion flow.
- Consider a simulator burst profile or command mode for explicit load tests instead of only increasing `events-per-tick`.
- Consider configurable data distributions for users, tables, sensitive-table targeting, and high-risk queries once alert rules exist.
- Decide whether simulator publish failures should remain log-only or feed operational metrics.

### Anomaly Detection

- Implement rolling baselines by user/table/query type.
- Start with moving average or z-score logic before adding heavier ML approaches.
- Persist anomaly alerts through the same alert pipeline.
- Add tests for normal baseline updates and outlier detection.
- Decide whether the anomaly detector should live in Java first or in the future `analytics_engine` Python service.

### Security

- Replace permit-all security with an API key or basic auth.
- Keep health endpoints available as appropriate for local Compose health checks.
- Add tests proving protected endpoints reject unauthenticated requests and accept valid credentials.
- Avoid logging secrets or raw credentials.

### Persistence and Operations

- Introduce database migrations with Flyway or Liquibase before schema changes become harder to manage.
- Review indexes for alert filtering and policy lookup once those APIs exist.
- Add database-level constraints that match application validation where practical.
- Add RabbitMQ dead-letter exchange/queue handling for raw ingestion messages that exceed listener retry attempts.
- Consider a dead-letter status or table for ingestion events that exceed processor max retries.
- Add operational metrics for accepted events, processed events, failed events, retry counts, and queue depth.
- Add simulator metrics for published events, retry attempts, and failed publishes if load testing becomes a regular workflow.

### Distributed/Streaming Stretch

- If multiple ingestion instances become part of the design, replace or augment the in-memory queue with one of:
  - atomic row claiming with conditional updates,
  - PostgreSQL `FOR UPDATE SKIP LOCKED`,
  - Redis Streams,
  - Kafka.
- Use the Compose `redis` streaming profile or add Kafka when the project reaches Week 4 stretch goals.
- Add idempotency or duplicate protection before running multiple processors.

### Dashboard

- Create the `dashboard` service referenced by Compose.
- Show live alert counts, recent alerts, and ingestion processing status.
- Add filters by user, table, severity, and time range.
- Keep the dashboard backed by API DTOs, not direct database access.

## Cleanup Tasks

- Add a README with local development commands.
- Document request/response examples for `/events`.
- Add Compose command examples for PostgreSQL only and the full `app` profile.
- Document RabbitMQ management UI credentials and queue/exchange names for local development.
- Document traffic simulator RabbitMQ configuration and common load-test settings.
- Consider setting `spring.jpa.open-in-view=false` once API read paths are implemented.
- Review Log4j2 pattern defaults so empty MDC fields render cleanly.

