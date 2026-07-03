# TASK.md

This task list reflects the current state of the ingestion service plus the remaining work from `project-plan-doc.md`.

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

Known constraints:
- The in-memory queue is safe for the current single-ingestion-instance simulation, but it is not a distributed queue.
- Queued rows are not claimed atomically across multiple app instances.
- Security currently permits all requests; this is intentional for early ingestion work but must change before exposing the API.
- Hibernate `ddl-auto=update` is being used for development instead of a migration tool.

## Next Tasks

### Week 1 Completion: Rule-Based Alerts

- Add a rule evaluation service that runs after an `AccessEvent` is created.
- Persist alerts to the existing `alerts` table.
- Implement initial hardcoded rules:
  - access to a sensitive table,
  - access outside normal hours,
  - `DELETE` query without a `WHERE` clause,
  - unusually high row count for a single event.
- Add tests for each rule and for the no-alert path.
- Decide whether alert generation belongs directly in `IngestionQueueProcessor` after `AccessEvent` creation or in a separate domain service invoked from the processor.

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

### Traffic Simulator

- Create the `traffic_simulator` service referenced by Compose.
- Generate realistic JSON events with username, table name, query type, timestamp, row count, source IP, and query text.
- Support burst mode for load testing async ingestion.
- Add configuration for target ingestion URL and event rate.
- Document how to run simulator plus ingestion API through Compose.

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
- Consider a dead-letter status or table for ingestion events that exceed max retries.
- Add operational metrics for accepted events, processed events, failed events, retry counts, and queue depth.

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
- Add Compose command examples for PostgreSQL only, ingestion API, and future profiles.
- Consider setting `spring.jpa.open-in-view=false` once API read paths are implemented.
- Review Log4j2 pattern defaults so empty MDC fields render cleanly.
