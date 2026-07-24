# Spring Initializr Manual — Auction Platform

Scaffold each service at https://start.spring.io. Coding is yours; this only gets the boilerplate right.

## Global settings (every service)

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | **3.4.x** (ADR-0005) |
| Java | **21** |
| Group | `com.auction` |
| Packaging | Jar |
| Spring Cloud train | **2024.0.x** (aligns with Boot 3.4 — set via the Spring Cloud BOM, Initializr picks it when you add a cloud dep) |

Artifact = service name (`userservice`, `auctionservice`, ...). Rename existing `com.fitness` → `com.auction`.

## Common dependencies (add to every Spring service)

- **Lombok**
- **Spring Boot Actuator** (health + Micrometer)
- **Micrometer Tracing Bridge (OTel)** + **Zipkin reporter** (observability, ADR / decision B)

## Per-service dependencies

Legend: Web = Spring Web (MVC) · JPA = Spring Data JPA · PG = PostgreSQL Driver · Val = Validation · Eureka = Eureka Discovery Client · RS = OAuth2 Resource Server · AMQP = Spring for RabbitMQ · Redis = Spring Data Redis · WS = WebSocket · R4j = Resilience4j · TC = Testcontainers (test scope)

| Service | Dependencies | Notes |
|---|---|---|
| **discovery** | `Eureka Server` (spring-cloud-starter-netflix-eureka-server), Actuator | No Web/JPA. `@EnableEurekaServer`. Register-with-self = false. |
| **gateway** | `Gateway` (spring-cloud-starter-gateway), Eureka, RS, Actuator, tracing | **Reactive/WebFlux — do NOT add Spring Web (MVC).** Adding `spring-boot-starter-web` breaks Gateway. RS validates JWT here. |
| **userservice** | Web, JPA, PG, Val, Eureka, RS, Lombok, Actuator, tracing, **TC(postgres)** | Fix existing issues above. No password field (Keycloak owns creds). Profile + contact + role only. |
| **auctionservice** | Web, JPA, PG, Val, Eureka, RS, **AMQP**, **Redis**, **WS**, R4j, Lombok, Actuator, tracing, **TC(postgres+rabbitmq)** | The heart. Bidding lives here (ADR-0001). Optimistic lock (ADR-0002). |
| **agentservice** | Web, JPA, PG, Eureka, RS, AMQP, R4j, Lombok, Actuator, tracing, TC | Long-running proxy-bidder loops. Persists Proxy Bidder config/budget. Calls auctionservice API under a token. |
| **aiservice** | Web, Eureka, RS, R4j, Lombok, Actuator, tracing | Thin Gemini wrapper. Circuit breaker on Gemini (R4j). Use `RestClient` for Gemini REST, OR add **Spring AI Vertex Gemini** starter for an abstraction — optional, ponytail: RestClient is enough to start. No DB, no AMQP (called sync by agent). |
| **notificationservice** | AMQP, Eureka, Actuator, tracing, Lombok | Pure consumer. Log-only first (no DB, no mail). Add mail later. |
| **chatservice** | Web, JPA, PG, Val, Eureka, RS, **WS**, AMQP, Lombok, Actuator, tracing, TC | 1:1 text chat, post-close. Consumes `deal.created` to open a Chat Thread. |

## Not on Initializr (add manually to pom later)

- **Keycloak**: no starter needed — services are OAuth2 **Resource Servers** validating Keycloak-issued JWTs. Only `spring-boot-starter-oauth2-resource-server` (= RS above) + `issuer-uri` config.
- **Flyway** (`flyway-core`): add when you drop `ddl-auto: update`. Not v1.
- **Spring Cloud Config**: skip (YAGNI — env vars + per-service yml until it hurts).

## Package structure (per service, clean layering)

```
com.auction.<service>
├── <Service>Application.java
├── config/         Beans, security, WS, Rabbit, Redis config
├── controller/     REST + WS controllers (thin — no logic)
├── service/        Business logic (the part you own)
├── repository/     Spring Data interfaces (DAO pattern)
├── model/          JPA entities / domain
├── dto/            Request/response records — never expose entities
└── event/          Rabbit publishers + listeners, event payloads
```

Rules: controllers thin, no business logic. Never return entities from controllers — map to DTO `record`s. Constructor injection only (no field `@Autowired`).
