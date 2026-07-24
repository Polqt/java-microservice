# Spec: Auction Bid Placement (real-time English auction core)

Status: ready-for-agent

## Problem Statement

A Bidder viewing a live Auction wants to place a Bid and know *immediately* whether it won the current lead or was beaten. On a popular Auction, many Bidders bid within the same instant. Without correct concurrency handling, two Bids can both believe they won, corrupting the Auction's highest Bid and producing a disputed winner at Close.

## Solution

`auctionservice` accepts a Bid via a synchronous request and returns an immediate, authoritative result: accepted (new lead) or rejected (beaten / stale / below increment). Exactly one Bid can hold the lead at any moment, even under simultaneous Bids, because Bids are serialized through optimistic version checks rather than trusting read-then-write. Accepted Bids publish a `bid.placed` fact so the live feed and Proxy Bidders can react.

## User Stories

1. As a Bidder, I want to place a Bid on an open Auction, so that I can try to win the Item.
2. As a Bidder, I want an immediate accepted/rejected response, so that I know whether I currently lead.
3. As a Bidder, I want a Bid at or below the current highest Bid rejected, so that the leader is never wrongly overwritten.
4. As a Bidder, I want a Bid that does not meet the minimum increment rejected, so that trivial cent-bumps are prevented.
5. As a Bidder, I want my Bid rejected if the Auction has already Closed, so that I cannot bid on a finished Auction.
6. As a Bidder, I want my Bid rejected if the Auction has not yet started, so that early Bids cannot land.
7. As a Bidder, I want to be told when I have been outbid, so that I can decide to bid again.
8. As a Bidder, I want two of my rapid Bids to never both count as leader, so that I do not bid against myself by accident.
9. As a Seller, I want Bids on my own Auction blocked, so that shill bidding via my own account is impossible.
10. As a Seller, I want every accepted Bid to raise the current price correctly, so that Close reflects the true highest Bid.
11. As a Bidder, I want the current highest Bid and price served fast, so that the live view stays responsive under load.
12. As a system, I want each accepted Bid to emit a `bid.placed` event exactly once, so that downstream consumers (live feed, Proxy Bidder, notification) react reliably.
13. As a losing Bidder in a concurrent race, I want a clear conflict result, so that my client can retry against fresh state.
14. As an unauthenticated user, I want Bid placement rejected, so that only known Bidders can bid.
15. As an operator, I want a Bid on a non-existent Auction to return not-found, so that bad references fail cleanly.
16. As a Proxy Bidder, I want the same Bid API and rules as a human, so that no separate bidding path exists to keep consistent.
17. As a Bidder, I want a Bid below the Auction's starting price rejected, so that the floor is enforced.

## Implementation Decisions

- **Module:** all work in `auctionservice`. Bidding is NOT a separate service (ADR-0001 — bids and Auction state share one consistency boundary).
- **Concurrency:** optimistic locking via a JPA `@Version` on the Auction (or its current-lead state), with bounded retry on version conflict. No pessimistic locks, no Redis-authoritative counter (ADR-0002).
- **Balking:** a Bid that loses the version race, or is below `currentPrice + minIncrement`, is rejected outright — never queued or retried on the server's behalf.
- **API contract (synchronous):**
  - `POST /api/auctions/{auctionId}/bids` — body: bid amount. Auth required (Bidder identity from JWT, not the body).
  - `201 Created` + new lead state when accepted.
  - `409 Conflict` when beaten / version race lost / below increment (balked).
  - `422` (or `400`) when below starting price / malformed.
  - `404` when Auction does not exist.
  - `403` when the Bidder is the Seller, or Auction not in an open window.
- **Bid amount is authoritative from the request; Bidder identity is authoritative from the JWT** — never trust a bidderId in the body.
- **Event:** on accept, publish `bid.placed` (thin — auctionId, bidId, bidderId, amount, timestamp) to the RabbitMQ topic exchange. Consumers re-fetch detail via API if needed.
- **Read cache:** current price / highest Bid may be served from Redis for the live view; Postgres remains source of truth. Cache is read-only and best-effort.
- **Auction state** has an explicit open window (start, end) and status; Bid acceptance checks the window.

## Testing Decisions

- **Good test = external behavior only.** Drive the HTTP API; assert responses and persisted lead state. Do not assert on `@Version` numbers, retry counts, or internal method calls.
- **One seam (highest possible): the `auctionservice` HTTP API**, exercised with **Testcontainers** (real Postgres, real RabbitMQ). Prefer this single seam over unit-mocking the repository.
- **Headline test (the reason this spec exists):** fire two concurrent `POST .../bids` for equal/racing amounts against one Auction → assert **exactly one `201`, exactly one `409`**, and the persisted current lead equals the accepted Bid. This proves optimistic locking + balking.
- **Supporting API tests:** bid below increment → 409; bid below starting price → 422; bid on closed Auction → 403; bid by Seller → 403; bid on missing Auction → 404; unauthenticated → 401; accepted bid → `bid.placed` published exactly once (assert against a test Rabbit consumer / Testcontainers RabbitMQ).
- **Prior art:** none yet — this is the first Testcontainers integration test in the repo; it sets the pattern later services (chat, agent) copy.

## Out of Scope

- Auction creation / listing / lifecycle scheduling (separate spec).
- Close logic and winner selection (separate spec — saga/idempotency lives there).
- Proxy Bidder decision logic (separate spec) — this spec only guarantees agents use the same Bid API.
- Live WebSocket feed transport (separate spec) — this spec only emits the `bid.placed` event the feed will consume.
- Notifications, chat, AI advisory.
- Non-English auction types (English only, v1 — factory seam exists but not exercised here).
- Real payment (none — ADR-0004).

## Further Notes

- Coding is the developer's; scaffolding + review only. See `docs/setup/spring-initializr-manual.md` for how `auctionservice` is created.
- This is the walking-skeleton's hardest local problem (build order step 2) — get it correct and tested before wiring gateway/Eureka/events on top.
- Glossary: use Bid / Bidder / Auction / Proxy Bidder / balked as defined in `CONTEXT.md`. Avoid "offer", "buyer", "bot".
