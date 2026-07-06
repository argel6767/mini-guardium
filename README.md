# MiniGuardium

MiniGuardium is a small Guardium-style database activity monitoring project. It simulates database access events, routes them through RabbitMQ, persists them through an ingestion service, evaluates processed access events, stores alerts, and exposes alert data through a live dashboard.

The project is intentionally built as a simplified real-time monitoring system for learning and iteration. It is also a learning exercise for using Codex and CLI-based AI agent tools to plan, implement, review, and orchestrate a multi-service project while keeping the codebase understandable.

## Current Status

Implemented today:

- A Spring Boot ingestion service that accepts database activity events.
- A Spring Boot traffic simulator that generates synthetic database activity.
- RabbitMQ transport from simulator to ingestion.
- PostgreSQL persistence for raw ingestion records, normalized access events, users, tables, and alerts.
- A fallback `POST /events` HTTP ingestion endpoint.
- RabbitMQ publication after `AccessEvent` creation.
- A Spring Boot evaluation service that consumes processed access-event messages.
- Rule-based alert severity evaluation and alert persistence.
- Alert REST endpoints for list and summary data.
- Alert Server-Sent Events endpoints for severity notifications, batched alert updates, and alert-rate snapshots.
- Docker Compose wiring for PostgreSQL, RabbitMQ, ingestion, evaluation, simulator, and dashboard.
- React/TypeScript dashboard using Vite, Tailwind CSS, shadcn-compatible UI primitives, axios, React Query, and Jest.
- Dashboard widgets for summary metrics, severity mix, recent alerts, latest live alert, live alert rate, and per-severity live rate lines.
- Evaluation service CORS configured from `APP_FRONTEND_ALLOWED_ORIGIN` for the dashboard origin.

Not implemented yet:

- Configurable policy APIs.
- Production authentication and authorization.
- Database migrations.
- Rolling-baseline anomaly detection.

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
  -> RabbitMQ queue: guardium.access-events.evaluation-service
  -> evaluation_service AccessEventCreatedListener
  -> alerts row when rule scoring produces a severity
  -> REST and SSE alert APIs
  -> dashboard UI
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
|-- dashboard/
|-- evaluation_service/
|-- ingestion_processor/
`-- traffic_simulator/
```

## Components

### ingestion_processor

`ingestion_processor` is the ingestion API and worker service.

Responsibilities:

- Consumes raw ingestion messages from RabbitMQ.
- Accepts fallback HTTP ingestion through `POST /events`.
- Validates incoming event DTOs.
- Persists raw ingestion work to `ingestion_events`.
- Processes queued ingestion work asynchronously after transaction commit.
- Normalizes events into `access_events`.
- Publishes processed access-event messages to RabbitMQ for evaluation.
- Retries failed processing with backoff and retry metadata.

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
- Generates timestamps across a rolling 14-day window and keeps them out of the future.
- Uses role-aware time-of-day probabilities so most synthetic traffic occurs during expected hours, with occasional suspicious timing.

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

`evaluation_service` consumes processed access events and owns alert creation and dashboard-facing alert data.

Responsibilities:

- Declares and consumes `guardium.access-events.evaluation-service`.
- Loads the persisted `AccessEvent` created by ingestion.
- Scores access events using hardcoded rule signals.
- Persists `Alert` rows when the score reaches alert severity.
- Publishes in-process alert-created events for streaming clients.
- Exposes alert list, summary, severity stream, batch stream, and rate stream endpoints.

Current rule signals include:

- Sensitive table access.
- Access outside the role's expected hours.
- `DELETE` without a `WHERE` clause.
- High row count for a single event.
- Role/table permission mismatch checks.

Current alert endpoints are served from the evaluation service on `localhost:8081` in Compose:

```text
GET /alerts
GET /alerts/summary
GET /alerts/stream/severity
GET /alerts/stream/batches
GET /alerts/stream/rates
```

Important configuration:

```properties
evaluation.access-events.exchange=guardium.access-events
evaluation.access-events.queue=guardium.access-events.evaluation-service
evaluation.access-events.routing-key=access-event.created
```

### dashboard

`dashboard` is the React dashboard for alert visibility.

Responsibilities:

- Uses Vite, TypeScript, Tailwind CSS, and shadcn-compatible UI primitives.
- Uses axios and React Query for REST requests.
- Uses custom hooks for alert REST APIs and SSE streams.
- Renders summary metrics, severity mix, recent alerts, latest live alert, live alert rate, and per-severity live rate lines.
- Provides general loading and error states for API-backed widgets.
- Runs Jest/React Testing Library tests from `dashboard/tests`.
- Builds into an Nginx-served container on port `3000`.

Important configuration:

```text
VITE_EVALUATION_API_BASE_URL=http://localhost:8081
APP_FRONTEND_ALLOWED_ORIGIN=http://localhost:3000
```

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

Current queues:

- `guardium.ingestion-events.ingestion-processor`
- `guardium.access-events.evaluation-service`

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
- evaluation service
- traffic simulator
- dashboard

The dashboard is available at `http://localhost:3000`. The simulator waits for ingestion and evaluation to become healthy before publishing. This gives both direct-exchange queue bindings a chance to exist before synthetic traffic starts.

### Check Service Status

```powershell
docker compose --profile app ps
```

### Follow Logs

```powershell
docker compose --profile app logs -f ingestion-api
docker compose --profile app logs -f evaluation-service
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
  occurredAt = '2026-07-04T12:00:00Z'
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
  "acceptedAt": "2026-07-04T12:00:00Z"
}
```

The event is processed asynchronously after the ingestion row commits.

## Alert API Examples

The evaluation service listens on `localhost:8081` in the Compose app profile.

List alerts:

```powershell
Invoke-RestMethod 'http://localhost:8081/alerts?page=0&size=10'
```

Filter alerts:

```powershell
Invoke-RestMethod 'http://localhost:8081/alerts?severity=HIGH&username=alice&tableName=customer_accounts'
Invoke-RestMethod 'http://localhost:8081/alerts?createdFrom=2026-07-01T00:00:00Z&createdTo=2026-07-05T00:00:00Z'
```

Summary:

```powershell
Invoke-RestMethod 'http://localhost:8081/alerts/summary'
```

Server-Sent Events streams:

```powershell
curl.exe -N "http://localhost:8081/alerts/stream/severity"
curl.exe -N "http://localhost:8081/alerts/stream/batches"
curl.exe -N "http://localhost:8081/alerts/stream/rates"
```

## Useful Verification Commands

Check RabbitMQ queue depth:

```powershell
docker compose --profile app exec -T rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
```

Check persisted event and alert counts:

```powershell
docker compose --profile app exec -T postgres psql -U miniguardium -d miniguardium -c "select status, count(*) from ingestion_events group by status order by status; select count(*) as access_events from access_events; select severity, count(*) from alerts group by severity order by severity;"
```

Check recent service errors:

```powershell
docker compose --profile app logs --since=5m ingestion-api | Select-String -Pattern "ERROR|Exception|Failed"
docker compose --profile app logs --since=5m evaluation-service | Select-String -Pattern "ERROR|Exception|Failed"
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

Run dashboard checks:

```powershell
cd dashboard
pnpm lint
pnpm test --runInBand
pnpm exec tsc -b --noEmit
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
- Alert evaluation is rule-based and hardcoded; configurable policies and anomaly baselines are future work.
- Dashboard filtering and alert drill-down workflows are still follow-up work.

## More Detail

- See `ARCHITECTURE.md` for the current architecture and implementation notes.
- See `TASK.md` for the remaining roadmap and cleanup items.
- See `AGENTS.md` for project coding conventions and local environment notes.
