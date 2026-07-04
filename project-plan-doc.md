# MiniGuardium - A Simplified Real-Time DB Activity Monitor

MiniGuardium is a scoped-down clone of Guardium's core idea: watch database activity in near real time, flag suspicious behavior, and expose alert data through APIs that can support a dashboard.

The project has moved beyond the initial ingestion-only shape. It now has RabbitMQ-backed ingestion, normalized access-event persistence, rule-based evaluation, alert persistence, and dashboard-facing REST/SSE endpoints.

## Why This Project

It mirrors practical pieces of a database activity monitoring product:

- event ingestion,
- asynchronous processing,
- service-to-service messaging,
- rule evaluation,
- alert persistence,
- dashboard API design,
- future anomaly detection.

It is also being used as a learning project for CLI-based AI agent workflows with Codex: planning changes, applying incremental implementation steps, reviewing defects, and keeping documentation aligned as the system evolves.

## Current Tech Stack

- Java + Spring Boot for ingestion, simulation, evaluation, and APIs.
- PostgreSQL for application persistence.
- RabbitMQ for service-to-service event transport.
- Docker Compose for local orchestration.
- H2 for service tests.
- Future optional services: dashboard, anomaly detector, Redis or Kafka streaming.

## Current Architecture

```text
[Traffic Simulator]
    -> RabbitMQ guardium.ingestion-events
    -> [Ingestion Processor]
    -> PostgreSQL ingestion_events/access_events
    -> RabbitMQ guardium.access-events
    -> [Evaluation Service]
    -> PostgreSQL alerts
    -> REST/SSE Alert APIs
    -> [Future Dashboard]
```

Manual ingestion remains available:

```text
[Client]
    -> POST /events
    -> [Ingestion Processor]
    -> same async queue and evaluation path
```

## Implementation Progress

### Completed: Week 1 Data Model and Ingestion

- Fake DB traffic generator that emits JSON-like event payloads.
- Spring Boot ingestion endpoint `POST /events`.
- Persistent model for users, tables, ingestion events, access events, and alerts.
- RabbitMQ raw-ingestion path from simulator to ingestion.
- Durable ingestion queue table with retry metadata.
- Async processing into normalized `access_events`.
- Access-event-created messages published after database commit.

### Completed: Initial Rule-Based Alerts

- Spring Boot evaluation service.
- RabbitMQ consumer for processed access events.
- Rule scoring for suspicious access patterns.
- Severity calculation.
- Alert persistence.
- Tests for evaluation paths.

Current rule signals include:

- sensitive table access,
- access outside expected hours,
- highly suspicious access hours,
- unsafe delete checks,
- high row count,
- role/table permission mismatch.

### Completed: Initial API Visibility

- `GET /alerts` for paginated and filtered alert listing.
- `GET /alerts/summary` for aggregate dashboard data.
- `GET /alerts/stream/severity` for lightweight live alert notifications.
- `GET /alerts/stream/batches` for batched alert updates.
- `GET /alerts/stream/rates` for rolling alert-rate snapshots.

## Current Roadmap

### Next: Policies

- Add configurable sensitive tables and thresholds.
- Add role/table/query permissions as data instead of hardcoded maps.
- Add `/policies` endpoints with request validation.
- Apply policies during evaluation.
- Add integration tests for policy-backed rule behavior.

### Next: Dashboard

- Build the dashboard service currently represented as a Compose placeholder.
- Display alert summary, recent alerts, filtered alert table, and live stream updates.
- Render rolling alert rates as a line graph with multiple lines.
- Use only API DTOs; do not connect the dashboard directly to the database.

### Later: Anomaly Detection

- Build rolling baselines by user, table, and query type.
- Start with moving average or z-score logic.
- Persist anomaly alerts through the same alert pipeline.
- Decide whether the first implementation belongs in Java or in a future Python `analytics_engine` service.

### Later: Production Hardening

- Add API key or basic auth.
- Add database migrations with Flyway or Liquibase.
- Add dead-letter queue handling.
- Add metrics for ingestion, processing, alert creation, stream subscribers, and simulator publishing.
- Revisit distributed processing if multiple ingestion workers are introduced.

## Stretch Goals

- Multi-tenant policies.
- Rate-limiting or blocking simulation.
- Basic RBAC on APIs.
- Kafka or Redis Streams for a more realistic streaming architecture.
- Lightweight ML anomaly detection after simple baselines are working.
