# Auction Platform

A real-time, Philippines-focused online auction built as a Spring Boot microservice system.

Sellers list items. Bidders — human or **autonomous proxy-bidder agents** — compete in live ascending (English) auctions. When an auction closes, the platform picks the winner exactly once, creates a Deal, and introduces buyer and seller so they can settle off-platform.

No money moves through the platform. See [ADR-0004](docs/adr/0004-no-payment-off-platform-settlement.md).

---

## Why this project exists

A learning vehicle for distributed-system design in Java: concurrency under contention, event-driven integration, idempotency, and the operational side (observability, IaC, serverless deployment). Design decisions are recorded as ADRs rather than left implicit.

## The interesting problem

Two bidders bid on the same auction in the same millisecond. Both read `currentPrice = 100`. Both believe they won.

The platform resolves this with **optimistic locking** (`@Version` on the Auction) rather than pessimistic row locks, which would serialize every bid on a popular auction and collapse throughput. The loser of the race is **balked** — rejected outright with `409 Conflict` — never silently queued. Postgres stays the source of truth.

Rationale and rejected alternatives: [ADR-0002](docs/adr/0002-optimistic-locking-for-bids.md).

The same mechanism guarantees **exactly-once close**: concurrent close attempts race on the version, only one wins, and a unique constraint on `deals.auction_id` is the second net. Replaying a close returns the existing Deal and publishes no duplicate events.

---

## Architecture

```
                 ┌────────────────┐
                 │ gatewayservice │  Spring Cloud Gateway — routing, JWT validation
                 └───────┬────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
  ┌───────────┐   ┌─────────────┐  ┌────────────┐
  │userservice│   │auctionservice│  │chatservice │
  └───────────┘   └──────┬───────┘  └────────────┘
                         │ RabbitMQ (topic: auction.events)
              ┌──────────┼──────────┐
              │          │          │
      ┌──────────┐ ┌──────────┐ ┌──────────────────┐
      │agentservice│ │aiservice│ │notificationservice│
      └──────────┘ └──────────┘ └──────────────────┘

  discoveryservice (Eureka) — service registry
```

**Bidding lives inside `auctionservice`, not its own service.** Bids and auction state share one consistency boundary; splitting them would force a distributed lock for no benefit ([ADR-0001](docs/adr/0001-bidding-inside-auction-service.md)).

### Services

| Service | Port | Status | Responsibility |
|---|---|---|---|
| `discoveryservice` | 8761 | built | Eureka service registry |
| `gatewayservice` | 8080 | built | Entry point, routing, JWT validation |
| `userservice` | — | built | Profiles, contacts, roles |
| `auctionservice` | — | in progress | Auctions, bidding, close, winner selection, deals |
| `agentservice` | — | planned | Autonomous proxy bidders (rules-first) |
| `aiservice` | — | planned | Gemini advisory, behind a circuit breaker |
| `notificationservice` | — | planned | Event consumer — outbid, closing, won |
| `chatservice` | — | planned | 1:1 text chat, unlocked after close |

### Events

Published to a RabbitMQ topic exchange (`auction.events`) via `@TransactionalEventListener(AFTER_COMMIT)` — nothing leaks if the transaction rolls back. Events are thin facts (ids + minimal data); consumers re-fetch detail if they need it.

| Event | Emitted when |
|---|---|
| `bid.placed` | A bid is accepted |
| `auction.closed` | An auction closes |
| `deal.created` | A winner exists and the Deal is recorded |

### The agent

`agentservice` runs autonomous proxy bidders: a user sets a budget and strategy, the agent subscribes to `bid.placed` and counter-bids in real time. The decision core is **deterministic rules**; Gemini is consulted for a single advisory judgement (is this item worth stretching the soft cap?). If Gemini is unavailable, the circuit breaker opens and the agent falls back to pure rules. The budget is a hard ceiling — never exceeded, regardless of advice.

---

## Stack

**Backend** — Java 21 (LTS), Spring Boot 4.1, Spring Cloud 2025.x, Spring Data JPA, Spring Security (OAuth2 Resource Server)
**Data** — PostgreSQL (database-per-service), Redis (read cache, later slice)
**Messaging** — RabbitMQ (topic exchange)
**Auth** — Keycloak (OIDC); services validate JWTs, Keycloak owns credentials
**Real-time** — STOMP over WebSocket
**Frontend** — Angular (single SPA, lazy-loaded feature modules)
**Testing** — JUnit 5 + Testcontainers (real Postgres and RabbitMQ)
**Observability** — Actuator, Micrometer → Prometheus/Grafana, distributed tracing → Zipkin
**Infra** — Docker Compose locally; AWS Fargate + selective Lambda in production ([ADR-0003](docs/adr/0003-fargate-not-faas.md)), Terraform, GitHub Actions

Version rationale: [ADR-0005](docs/adr/0005-spring-boot-3-4-java-21-lts.md).

---

## Running locally

Requires Docker and JDK 21.

```bash
cp .env.example .env          # adjust credentials if you like
docker compose up -d          # Postgres, RabbitMQ, Keycloak
```

| Service | URL | Credentials |
|---|---|---|
| Postgres | `localhost:5432` | `auction` / `auction` |
| RabbitMQ management | http://localhost:15672 | `guest` / `guest` |
| Keycloak | http://localhost:8180 | `admin` / `admin` |

Databases are created per service on first Postgres start by [`infra/postgres/init`](infra/postgres/init/01-create-databases.sql). If the volume already exists, the init script does not re-run — `docker compose down -v` to reset.

Then run a service:

```bash
cd auctionservice && ./mvnw spring-boot:run
```

---

## API

```http
POST /api/auctions/{auctionId}/bids     # place a bid — synchronous, authoritative
POST /api/auctions/{auctionId}/close    # close an auction, select the winner, create the Deal
```

Bidding is deliberately **synchronous**: a bidder must know immediately whether their bid took the lead. Bidder identity comes from the JWT subject, never from the request body.

Responses use RFC 9457 `ProblemDetail`.

| Status | Meaning |
|---|---|
| `201` | Bid accepted, now leading |
| `403` | Auction not open, or bidder is the seller |
| `404` | Auction not found |
| `409` | Outbid, below the minimum increment, or lost the version race (balked) |
| `422` | Below the starting price |

---

## Project conventions

- **Domain language** is defined in [`CONTEXT.md`](CONTEXT.md) and used consistently in code, issues, and docs.
- **Decisions** that are hard to reverse are recorded in [`docs/adr/`](docs/adr/) — including the explicit *no*s.
- **Specs and issues** live as markdown under [`.scratch/`](.scratch/).
- Controllers stay thin and never accept entities as parameters; services own business logic; entities never cross the API boundary.

## Status

Early. Discovery, gateway, `userservice`, and the `auctionservice` bidding and close paths are implemented; the concurrency test, remaining services, frontend, and deployment are still ahead.

Start services in this order: `discoveryservice` → `gatewayservice` → the rest.
