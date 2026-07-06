# MiniGuardium Dashboard

The dashboard is a React/TypeScript frontend for MiniGuardium alert visibility. It uses Vite, Tailwind CSS, shadcn-compatible UI primitives, axios, React Query, and Jest/React Testing Library.

## Current Status

Implemented dashboard views and behavior:

- Dark Grafana-style operational shell.
- Alert summary metric cards from `GET /alerts/summary`.
- Recent alert list from `GET /alerts`.
- Severity mix panel from alert summary data.
- Latest live alert card from batched alert SSE events.
- Live overall alert rate from rate SSE events.
- Live line graph for average alerts per minute by severity.
- Loading and error states for API-backed widgets.
- Custom hooks for all REST and SSE requests.
- Component, hook, REST client, and SSE client tests under `tests`.

## API Integration

The dashboard talks to `evaluation_service` only through DTO-based REST and SSE APIs.

REST endpoints:

```text
GET /alerts
GET /alerts/summary
```

SSE endpoints:

```text
GET /alerts/stream/severity
GET /alerts/stream/batches
GET /alerts/stream/rates
```

The evaluation API base URL is configured at build/dev time with:

```text
VITE_EVALUATION_API_BASE_URL=http://localhost:8081
```

In Compose, `evaluation_service` allows the dashboard origin with:

```text
APP_FRONTEND_ALLOWED_ORIGIN=http://localhost:3000
```

## Local Development

Install dependencies with PNPM:

```powershell
pnpm install
```

Run the Vite dev server:

```powershell
pnpm dev
```

The dev server defaults to `http://localhost:5173`.

## Docker / Compose

The production dashboard image is built by `dashboard/Dockerfile` and served through Nginx on port `3000`.

Run the full app stack from the repository root:

```powershell
docker compose --profile app up -d --build
```

Open the dashboard at:

```text
http://localhost:3000
```

## Checks

Run lint:

```powershell
pnpm lint
```

Run tests:

```powershell
pnpm test --runInBand
```

Run TypeScript checking:

```powershell
pnpm exec tsc -b --noEmit
```

Build locally when validating production output:

```powershell
pnpm build
```

## Source Layout

```text
src/
|-- components/
|   |-- dashboard/
|   `-- ui/
|-- hooks/
|-- lib/
`-- main.tsx

tests/
|-- components/
|-- hooks/
|-- lib/
|-- mocks/
|-- setup.ts
`-- queryClientWrapper.tsx
```

Important files:

- `src/lib/api.ts`: axios REST client and SSE subscription helpers.
- `src/hooks/useAlerts.ts`: React Query hooks for REST requests.
- `src/hooks/useAlertStreams.ts`: hooks for alert SSE streams.
- `src/components/dashboard/MetricsGrid.tsx`: summary metrics, latest alert, live rate, and live chart composition.
- `src/components/dashboard/LiveSeverityRateChart.tsx`: SVG line chart for per-severity alert rates.
