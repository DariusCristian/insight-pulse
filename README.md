# InsightPulse

Real-time market intelligence platform that ingests financial event streams, runs ML inference, and surfaces actionable insights through a live dashboard.

## Project Overview

InsightPulse is a full-stack monorepo that combines:

- **Event ingestion** — A Java/Spring Boot service that consumes market data feeds, validates and enriches events, publishes them to Kafka, and persists results to PostgreSQL.
- **ML inference** — A Python/FastAPI service exposing both a REST API and a gRPC endpoint for low-latency model predictions over the event stream.
- **Live dashboard** — A Next.js frontend that streams real-time insights to analysts via WebSocket, backed by the ingestion service REST API.

For the full technical specification, data contracts, and ADRs see [SPECIFICATION.md](./SPECIFICATION.md).

## Architecture Diagram

```
 Market Data Feeds
        │
        ▼
┌──────────────────┐      Kafka       ┌───────────────────┐
│ ingestion-service│ ──────────────▶  │ inference-service │
│  (Java / Spring) │◀── gRPC (50051)──│ (Python / FastAPI)│
└────────┬─────────┘                  └───────────────────┘
         │ REST / WebSocket
         ▼
┌──────────────────┐
│dashboard-frontend│
│   (Next.js)      │
└──────────────────┘
         │
         ▼
    PostgreSQL
```

> See [SPECIFICATION.md](./SPECIFICATION.md) for the detailed component diagram and data-flow description.

## Prerequisites

| Tool        | Version  | Notes                          |
|-------------|----------|--------------------------------|
| Java        | 21+      | GraalVM or OpenJDK             |
| Python      | 3.11+    |                                |
| Node.js     | 20+      | LTS recommended                |
| Docker      | 24+      | Docker Desktop or Engine       |
| Docker Compose | 2.20+ | Bundled with Docker Desktop    |

## Quick Start

### Full stack via Docker Compose

```bash
# Clone the repo
git clone <repo-url> insight-pulse
cd insight-pulse

# Copy root env example and adjust as needed
cp .env.example .env

# Build images and start all services
docker compose up --build

# Detached mode
docker compose up --build -d

# Tear down (preserves volumes)
docker compose down

# Tear down and wipe all data volumes
docker compose down -v
```

### Individual service startup (local development)

**ingestion-service** (Java / Gradle):
```bash
cd ingestion-service
cp .env.example .env   # edit as needed
./gradlew bootRun
```

**inference-service** (Python / FastAPI):
```bash
cd inference-service
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --port 8000
```

**dashboard-frontend** (Next.js):
```bash
cd dashboard-frontend
npm install
cp .env.local.example .env.local   # edit as needed
npm run dev
```

## Service Ports

| Service            | Port  | Protocol   | Description                         |
|--------------------|-------|------------|-------------------------------------|
| ingestion-service  | 8080  | HTTP       | REST API + WebSocket                |
| inference-service  | 8000  | HTTP       | FastAPI REST endpoints              |
| inference-service  | 50051 | gRPC       | Protobuf prediction endpoint        |
| dashboard-frontend | 3000  | HTTP       | Next.js web UI                      |
| kafka              | 9092  | PLAINTEXT  | Internal broker (container network) |
| kafka              | 29092 | PLAINTEXT  | External broker (host machine)      |
| kafka-ui           | 8090  | HTTP       | Kafka cluster management UI         |
| postgres           | 5432  | TCP        | PostgreSQL database                 |
| zookeeper          | 2181  | TCP        | Kafka coordination                  |

## Development Workflow

1. **Start infrastructure only** (Kafka, Zookeeper, PostgreSQL) so services run locally:
   ```bash
   docker compose up -d zookeeper kafka kafka-ui postgres
   ```

2. **Run services locally** pointing at the Docker infrastructure (use `.env` files per service — see [Environment Variables](#environment-variables)).

3. **Hot reload** is available in every service:
   - ingestion-service: `./gradlew bootRun` (Spring DevTools)
   - inference-service: `uvicorn ... --reload`
   - dashboard-frontend: `npm run dev` (Next.js fast refresh)

4. **Run tests**:
   ```bash
   # ingestion-service
   cd ingestion-service && ./gradlew test

   # inference-service
   cd inference-service && pytest

   # dashboard-frontend
   cd dashboard-frontend && npm test
   ```

5. **Lint & format**:
   ```bash
   # ingestion-service
   ./gradlew spotlessApply

   # inference-service
   ruff check . && ruff format .

   # dashboard-frontend
   npm run lint
   ```

6. **Simulate market events** (stack must be running; needs only Python 3 and Docker):
   ```bash
   python3 scripts/simulate_events.py                       # 100 events at 2/s
   python3 scripts/simulate_events.py --count 0 --rate 5    # stream until Ctrl+C
   python3 scripts/simulate_events.py --malformed-every 20  # also exercise the DLQ
   python3 scripts/simulate_events.py --help                # all options
   ```
   Open http://localhost:3000 to watch the events arrive. Tests: `python3 -m unittest discover -s scripts`.

## Environment Variables

Each service ships an `.env.example` that documents all required variables:

| Service            | File                                        |
|--------------------|---------------------------------------------|
| Root (shared)      | `.env.example` (this directory)             |
| ingestion-service  | `ingestion-service/.env.example`            |
| inference-service  | `inference-service/.env.example`            |
| dashboard-frontend | `dashboard-frontend/.env.local.example`     |

Copy the relevant example file, rename it (remove `.example` / `.local.example`), and fill in any secrets before running.

## Project Structure

```
insight-pulse/
├── docker-compose.yml          # Full-stack orchestration
├── .env.example                # Root-level shared env vars (template)
├── .gitignore
├── README.md
├── SPECIFICATION.md            # Architecture spec & ADRs
│
├── ingestion-service/          # Java 21 / Spring Boot 3
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── Dockerfile
│   ├── .env.example
│   └── .dockerignore
│
├── inference-service/          # Python 3.11 / FastAPI + gRPC
│   ├── app/
│   │   ├── main.py
│   │   ├── grpc_server.py
│   │   └── models/
│   ├── proto/
│   ├── tests/
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── .env.example
│   └── .dockerignore
│
└── dashboard-frontend/         # Next.js 14 / TypeScript
    ├── src/
    │   ├── app/
    │   ├── components/
    │   └── lib/
    ├── public/
    ├── package.json
    ├── tsconfig.json
    ├── Dockerfile
    ├── .env.local.example
    └── .dockerignore
```
