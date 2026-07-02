# MiniGuardium - A Simplified Real-Time DB Activity Monitor

A scoped-down clone of Guardium's core idea: watch database activity in real time, flag suspicious behavior, and expose it through an API/dashboard. Sized for ~3-4 weeks, part-time.

## Why this project
It mirrors the actual pieces you'll likely touch at IBM: event ingestion, real-time analytics/anomaly detection, and a service layer around it - without needing Guardium's actual codebase or infra.

## Tech Stack (mirrors likely Guardium stack)
- **Core service:** Java + Spring Boot (REST API, event ingestion)
- **Analytics engine:** Python (stats/anomaly detection) - or Java if you want to stay in one language
- **Storage:** PostgreSQL (or SQLite to keep it light)
- **Messaging (stretch):** Kafka or Redis Streams for real event streaming
- **Containerization:** Docker (+ optional docker-compose)

## Architecture
```
[DB Traffic Simulator] -> [Ingestion API] -> [Event Store]
                                |
                       [Anomaly Detector]
                                |
                    [Alerts API + Dashboard]
```

## Week-by-Week Plan

**Week 1 - Data model + ingestion**
- Build a fake DB-traffic generator: emits JSON events (user, table, query type, timestamp, row count, source IP)
- Spring Boot REST endpoint `/events` that accepts and stores these
- Data model: `users`, `tables`, `access_events`, `alerts`
- Simple rule engine: flag hardcoded bad patterns (e.g., access to a "sensitive" table, 2am access, DELETE without WHERE)

**Week 2 - Real anomaly detection**
- Rolling baseline per user/table: average query volume, typical access hours
- Z-score or moving-average based outlier detection ("this user just did 50x their normal query volume")
- Flag events that break baseline -> write to `alerts`
- This is the closest analog to what Shpak's RTTE work is likely doing

**Week 3 - API + visibility**
- `/alerts` endpoint: list/filter/query alerts
- `/policies` endpoint: let a user define what counts as "sensitive" (table names, thresholds)
- Basic auth or API key on endpoints (mirrors compliance-mindset of the product)
- Dockerize the whole thing

**Week 4 (stretch) - Make it "real-time"**
- Swap synchronous ingestion for Kafka: simulator -> topic -> detector consumer
- Add a minimal dashboard (even a simple React page or Grafana on top of Postgres) showing live alerts
- Try a lightweight ML model (isolation forest via scikit-learn) instead of z-score, compare results

## Stretch goals if you want more
- Multi-tenant policies (different rules per "database")
- Rate-limiting/blocking simulation - not just alerting, but auto-blocking a "connection"
- Basic RBAC on the API

## What this teaches you that's directly relevant
- Designing an ingestion -> detection -> alert pipeline (the shape of Guardium's real-time engine)
- Baseline/anomaly detection thinking - useful vocabulary for talking to Shpak about RTTE
- Spring Boot fluency if that's the Guardium stack, without waiting to find out on the job
