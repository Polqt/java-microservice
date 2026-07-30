# Review notes — auctionservice

## Resolved

Fixed in the hardening pass. Both services compile.

| # | Issue | Fix |
|---|---|---|
| 1 | **Close had no authorization** — any authenticated user could close any auction and mint its Deal | `closeAuction(auctionId, callerId)`; seller check in the service against the loaded auction; new `NotAuctionOwnerException` → 403. Controller passes `jwt.getSubject()` |
| 2 | **Both services shared one database** (`${POSTGRES_DB}`), defeating DB-per-service | Database name hardcoded per service (`/auctionservice`, `/userservice`); host stays configurable via `POSTGRES_HOST` |
| 3 | **`reactionId` accepted, validated, then discarded** — agent retries placed duplicate Bids | Persisted on `Bid` with a unique constraint; `placeBid` returns the original Bid on replay. 3-arg overload keeps the human path unchanged |
| 4 | **Internal endpoints unreachable** — `@PreAuthorize` required client `agentservice`, which did not exist in the realm | Added `agentservice` confidential client (service accounts enabled) + `SERVICE_AGENT` realm role + its service-account user |
| 5 | **`aud` claim check could NPE → 500 instead of 403**, and `.contains()` silently did substring matching on a String claim | Replaced SpEL claim-string checks with `@PreAuthorize("hasRole('SERVICE_AGENT')")` at class level, using the existing `JwtAuthenticationConverter` |
| 6 | **`@EnableMethodSecurity` on a `@Service`** — configuration annotation in the wrong place | Removed (already correct on `SecurityConfig`) |
| 7 | **Full table scan selecting the winning Bid** — no index behind `findTopByAuctionIdOrderByAmountDescPlacedAtAsc` | Composite index on `bids(auctionId, amount, placedAt)`, plus one on `bidderId` |
| 8 | **`jakarta.transaction.Transactional`** — no `readOnly`, no `rollbackFor`, no propagation | Switched to Spring's; `getBidState` is now `readOnly = true` |
| 9 | **Internal bid returned 200, public returned 201** for the same operation | Both 201 |
| 10 | **`OptimisticLockingFailureException` unmapped** — a lost race would surface as 500 | Mapped to 409 with a re-read message |
| 11 | **userservice had no transaction boundaries** — `editProfile`'s read-then-write was not atomic | `@Transactional` on writes, `readOnly` on reads |
| 12 | **No way to obtain a token locally** — no realm users, direct access grants disabled | Seeded `seller1`, `bidder1`, `bidder2` (password `password`); enabled direct access grants on `auction-web`, flagged dev-only in the realm file |

Two bidders are seeded deliberately — the concurrency test needs distinct racing identities.

---

## Still open

### The concurrency test does not exist

Only Initializr stubs in `src/test`. `TestcontainersConfiguration` is scaffolded, so the seam is ready.

From [spec.md](spec.md):

> Two concurrent `POST /api/auctions/{id}/bids` against one auction → exactly one `201`, exactly one `409`, and the persisted lead equals the accepted bid.

Shape: `@SpringBootTest` + Testcontainers Postgres, two threads released together by a `CountDownLatch`, collect both statuses, assert the pair. Drive the HTTP API, not the service.

Then: below increment → 409; below starting price → 422; closed → 403; seller bidding → 403; missing auction → 404; unauthenticated → 401.

### Spec drift

[spec.md](spec.md) lists close, winner selection, and the agent API as **out of scope**, but all three are built. Either write `auction-close` and `agent-integration` specs, or widen this one. A reviewer comparing spec to code sees drift today.

### Events can still be lost after commit

`AFTER_COMMIT` publishing means a Rabbit outage after the transaction commits loses the event silently. The fix is a transactional outbox (persist the event in the same transaction, relay it separately). Correct call to defer — but know the gap exists rather than assuming delivery is guaranteed.

### Auction has no creation endpoint

Nothing creates an Auction, so bidding cannot be exercised through the API at all — tests and manual runs both need seeded rows. Smallest unblock for end-to-end testing.

---

## Deliberate simplifications — leave as-is

- **No retry loop in `placeBid`.** The race loser gets 409 and re-bids. Contract holds; add bounded retry only if rejection rates hurt real users.
- **Close is a manual `POST`.** Scheduled sweep (`auction.closing`, auto-close at `endAt`) is a later slice.
- **Events carry ids, not entities.** Extra fetch for consumers, no stale-data coupling.
- **`ddl-auto: update`.** Fine while learning; replace with Flyway before schema changes matter.

## Already correct — do not "fix" these

- `saveAndFlush` in `placeBid` is deliberate: forces the version check inside the method, so the conflict surfaces there rather than at commit.
- `@TransactionalEventListener(AFTER_COMMIT)` — nothing is published if the transaction rolls back.
- The `CLOSED` branch of `closeAuction` returns the existing Deal and publishes nothing — replay-safe.
- `findTopByAuctionIdOrderByAmountDescPlacedAtAsc` — highest amount, earliest bid breaks ties.
- Money comparisons use `compareTo`, never `equals`.
- Unique constraint on `deals.auction_id` is the second net behind the version check.
- `@Version` is `Long`, not a timestamp — timestamps tie within a clock tick.
