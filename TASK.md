# TASK.md

This task list reflects the current state of the ingestion service, traffic simulator, evaluation service, Compose setup, and remaining work from `project-plan-doc.md`.

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
- The simulator generates `occurredAt` values inside a rolling 14-day window and never in the future.
- The simulator uses role-aware expected, suspicious, and highly suspicious time windows to avoid every generated event becoming high severity.
- RabbitMQ is part of the Compose app profile and is used for service-to-service event transport.
- The simulator publishes raw ingestion events to RabbitMQ instead of calling ingestion over HTTP.
- The ingestion service consumes raw ingestion events from RabbitMQ and maps them into the existing durable ingestion queue path.
- `POST /events` remains available as a fallback/manual ingestion API.
- Ingestion publishes access-event-created messages to RabbitMQ after the `AccessEvent` creation transaction commits.
- Spring AMQP listener retry is configured for raw ingestion consumer failures.
- Spring Boot evaluation service exists under `evaluation_service`.
- The evaluation service consumes processed access-event messages from RabbitMQ.
- The evaluation service declares a durable `guardium.access-events.evaluation-service` queue and binding.
- Compose starts evaluation service in the `app` profile and the traffic simulator waits for evaluation health before publishing.
- Evaluation persists alerts to the `alerts` table when rule scoring produces a severity.
- Evaluation handles real simulator values for usernames and table names, not only enum-style names.
- Evaluation maps all current simulator query types in `getTablePermission()`.
- Tests cover the evaluation algorithm paths and alert persistence.
- Alert dashboard endpoints exist: `GET /alerts` and `GET /alerts/summary`.
- Alert stream endpoints exist: `/alerts/stream/severity`, `/alerts/stream/batches`, and `/alerts/stream/rates`.
- Alert list filtering uses dynamic JPA specifications for optional filters.

Known constraints:

- The ingestion in-memory processing queue is safe for the current single-ingestion-instance simulation, but it is not a distributed worker queue.
- Raw ingestion delivery is brokered through RabbitMQ, but queued database rows are still not claimed atomically across multiple app instances.
- Simulator delivery is durable only after RabbitMQ accepts a message; it does not persist unsent events locally.
- Security currently permits all application requests; this is intentional for early local work but must change before exposing the APIs.
- Hibernate `ddl-auto=update` is being used for development instead of a migration tool.
- Alert rules are hardcoded in the evaluation service; configurable policies are not implemented yet.
- The dashboard service is still a placeholder.

## Next Tasks

### Policy Configuration

- Add a policy model for configurable sensitive tables, thresholds, role permissions, and expected access windows.
- Add `/policies` endpoints for creating, updating, listing, and deleting policies.
- Use DTOs and validation on all policy requests.
- Apply policies during rule evaluation without exposing JPA entities.
- Add tests for policy validation and rule behavior with configured policies.

### Dashboard

- Create the `dashboard` service referenced by Compose.
- Show live alert counts, recent alerts, and ingestion processing status.
- Use `GET /alerts/summary` for aggregate cards.
- Use `GET /alerts` for paginated and filtered alert tables.
- Use `/alerts/stream/severity` for lightweight live severity notifications.
- Use `/alerts/stream/batches` for batched alert table updates.
- Use `/alerts/stream/rates` for line graphs showing rolling alert rates overall and by type.
- Add filters by user, table, severity, and time range.
- Keep the dashboard backed by API DTOs, not direct database access.

### Alert API Follow-Up

- Decide whether alert pagination needs cursor-based pagination once dashboard traffic exists.
- Add indexes for alert filtering if PostgreSQL query plans show pressure.
- Consider an endpoint for single-alert detail if dashboard drill-down needs it.
- Consider an endpoint for recent alerts since a timestamp if SSE reconnect behavior needs explicit catch-up.

### Ingestion Visibility

- Add a simple `GET /events/{ingestionId}` endpoint to check queue status if clients need ingestion tracking.
- Keep the endpoint DTO-based and hide query text unless there is a clear need to expose it.
- Add tests for status lookup, not-found responses, and hidden fields.

### Traffic Simulator Follow-Up

- Document any new simulator profiles after dashboard work begins.
- Add an end-to-end Compose smoke test or manual verification script for simulator-to-RabbitMQ-to-ingestion-to-evaluation flow.
- Consider a simulator burst profile or command mode for explicit load tests instead of only increasing `events-per-tick`.
- Consider configurable data distributions for users, tables, sensitive-table targeting, and high-risk queries.
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
- Add RabbitMQ dead-letter exchange/queue handling for raw ingestion and access-event messages that exceed listener retry attempts.
- Consider a dead-letter status or table for ingestion events that exceed processor max retries.
- Add operational metrics for accepted events, processed events, failed events, retry counts, queue depth, alerts created, and stream subscribers.
- Add simulator metrics for published events, retry attempts, and failed publishes if load testing becomes a regular workflow.

### Distributed/Streaming Stretch

- If multiple ingestion instances become part of the design, replace or augment the in-memory queue with one of:
  - atomic row claiming with conditional updates,
  - PostgreSQL `FOR UPDATE SKIP LOCKED`,
  - Redis Streams,
  - Kafka.
- Use the Compose `redis` streaming profile or add Kafka when the project reaches stretch goals.
- Add idempotency or duplicate protection before running multiple processors.

## Cleanup Tasks

- README documents the project purpose, Codex learning goal, components, local run commands, `/events` examples, alert API examples, RabbitMQ details, and simulator settings.
- `spring.jpa.open-in-view=false` is configured in ingestion to avoid Open Session in View during request rendering.
- Review Log4j2 pattern defaults so empty MDC fields render cleanly.

