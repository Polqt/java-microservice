# Review notes — auctionservice

Open items from code review. Ordered: blockers first. Tick as you go.

---

## 1. Close endpoint has no authorization (security)

**Where:** `controller/AuctionController.java` — `closeAuction`, and `service/AuctionService.java` — `closeAuction`

Any authenticated user can close any auction and mint its Deal. The earlier check was removed and nothing replaced it.

The check belongs in the **service**, which holds the DB-loaded auction. The controller only passes identity down.

```java
// controller
@PostMapping("/{auctionId}/close")
public ResponseEntity<CloseAuctionResponse> closeAuction(
        @PathVariable String auctionId,
        @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(auctionService.closeAuction(auctionId, jwt.getSubject()));
}

// service, immediately after findById
if (!callerId.equals(auction.getSellerId())) {
    throw new NotAuctionOwnerException(auctionId);   // new exception -> 403 in GlobalExceptionHandler
}
```

Rule worth keeping: **never take an entity as a controller parameter.** Spring binds it from request params, producing a blank object that never touches the database. Controllers take ids and DTOs.

---

## 2. No datasource configured — the service will not start

**Where:** `src/main/resources/application.properties`

Currently only `spring.application.name` and the JWT issuer. JPA is on the classpath with no database configured, so startup fails.

Rename to `application.yml` (consistent with userservice) and add:

```yaml
spring:
  application:
    name: auctionservice

  datasource:
    url: jdbc:postgresql://localhost:5432/auctionservice
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: update      # fine while learning; replace with Flyway before it matters
    show-sql: true

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
```

Database `auctionservice` is already created by `infra/postgres/init/01-create-databases.sql`.

---

## 3. The concurrency test does not exist yet

**Where:** `src/test/java/...` — only the Initializr stub is there. `TestcontainersConfiguration` is already scaffolded, so the seam is ready.

This is the deliverable the whole spec exists for. Everything built so far — optimistic locking, balking, exactly-once close — rests on `@Version` behaving as expected, and none of it is currently proven.

The headline test, from [spec.md](spec.md):

> Fire two concurrent `POST /api/auctions/{id}/bids` at one auction → assert **exactly one `201`** and **exactly one `409`**, and that the persisted current lead equals the accepted bid.

Shape: `@SpringBootTest` with real Postgres via Testcontainers, two threads on a `CountDownLatch` so both bids land together, collect both status codes, assert the pair. Drive it through the HTTP API — the highest seam — not the service directly.

Supporting cases once that is green: below increment → 409; below starting price → 422; closed auction → 403; seller bidding → 403; missing auction → 404; unauthenticated → 401.

---

## 4. Minor

- **`jakarta.transaction.Transactional` → `org.springframework.transaction.annotation.Transactional`** in `AuctionService`. Spring's variant supports `readOnly`, `rollbackFor`, propagation and isolation; the Jakarta one does not.
- **Unused import** `java.time.Instant` in `model/Auction.java`.
- **Stray blank lines** in `AuctionService.closeAuction` around the winning-bid lookup.
- **Table naming** — `auctions`, `bids`, `deals` are plural; keep it that way everywhere.

---

## 5. Deliberate simplifications — leave as-is, mark the intent

Not defects. Worth a `// ponytail:` comment so a reader sees intent rather than oversight.

- **No retry loop in `placeBid`.** The race loser gets `OptimisticLockingFailureException` → `409`. A bid that might have won on retry is rejected instead. The API contract still holds. Add bounded retry only if the rejection rate hurts real users.
- **Close is a manual `POST`.** The scheduled sweep (`auction.closing` + automatic close at `endAt`) is a later slice.
- **Events carry ids and minimal data**, not full entities. Consumers re-fetch. Costs an extra call, avoids stale-data coupling.

---

## Already correct — do not "fix" these

Recorded so they do not get refactored away later:

- `saveAndFlush` in `placeBid` is deliberate: it forces the version check inside the method so the conflict surfaces there, not at commit time.
- `@TransactionalEventListener(AFTER_COMMIT)` means no event is published if the transaction rolls back.
- The `CLOSED` branch of `closeAuction` returns the existing Deal and publishes **nothing** — replay-safe.
- `findTopByAuctionIdOrderByAmountDescPlacedAtAsc` — highest amount, earliest bid wins ties. Correct winner rule.
- Money comparisons use `compareTo`, never `equals` (`BigDecimal` treats `100` and `100.0` as unequal).
- The unique constraint on `deals.auction_id` is the second net behind the version check for exactly-once Deal creation.
