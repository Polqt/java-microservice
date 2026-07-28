# Spec: Rules-First Proxy Bidder

Status: ready-for-agent

## Problem Statement

A Bidder cannot watch an Auction continuously. They want a Proxy Bidder to compete on their behalf without ever exceeding a hard Budget. The Proxy Bidder must react to accepted Bids in real time, use the same authoritative Auction rules as a human Bidder, avoid bidding against itself, and remain safe when RabbitMQ redelivers an event or an HTTP request is retried.

The existing human Bid endpoint derives the Bidder identity from the JWT subject. A long-running service cannot safely impersonate that human with its own Keycloak service-account token. Agent-initiated Bids therefore need an explicit service-authenticated entry point that still delegates to the same bidding rule engine and transaction boundary.

## Solution
d
A Bidder creates and controls a rules-first Proxy Bidder for one Auction. `agentservice` stores its Budget and state, consumes `bid.placed` and `auction.closed` facts from RabbitMQ, reads fresh authoritative bid state from `auctionservice`, and decides whether to react.

For V1, the strategy always proposes exactly the current price plus the Auction minimum increment. It skips the reaction when the Bidder already leads, the Proxy Bidder is inactive, the Auction is not open, the event was already handled, or the next amount would exceed the Budget.

Agent-initiated Bids use an internal `auctionservice` API available only to the authenticated `agentservice` Keycloak client. That API accepts the represented Bidder and a deterministic reaction identifier, then delegates to the same application service used by the human Bid API. `auctionservice` remains the sole authority for Bid validation, optimistic locking, persistence, and `bid.placed` publication.

## User Stories

1. As a Bidder, I want to create a Proxy Bidder for an Auction, so that it can compete while I am away.
2. As a Bidder, I want to set a hard Budget, so that no autonomous decision can exceed what I am willing to pay.
3. As a Bidder, I want my identity taken from my JWT when creating a Proxy Bidder, so that another user cannot create one in my name.
4. As a Bidder, I want only one Proxy Bidder for myself on one Auction, so that duplicate agents cannot compete against each other.
5. As a Bidder, I want to view my Proxy Bidder state, so that I know whether it is active, paused, or completed.
6. As a Bidder, I want to pause my Proxy Bidder, so that it stops reacting without deleting its audit history.
7. As a Bidder, I want to reactivate a paused Proxy Bidder while the Auction is open, so that it can resume competing.
8. As a Bidder, I want to change my Budget, so that I can respond to changing interest in the Item.
9. As a Bidder, I want another user blocked from viewing or changing my Proxy Bidder, so that my bidding intent and Budget remain private.
10. As a Bidder, I want invalid or non-positive Budgets rejected, so that an unusable Proxy Bidder is not created.
11. As a Bidder, I want a Budget below the Auction starting price rejected, so that activation has a meaningful bidding range.
12. As a Bidder, I want my Proxy Bidder to react only to accepted Bids, so that rejected attempts do not trigger unnecessary work.
13. As a Bidder, I want my Proxy Bidder to read fresh Auction state before reacting, so that a stale RabbitMQ event cannot produce a stale Bid.
14. As a Bidder, I want my Proxy Bidder to bid exactly the current price plus the minimum increment in V1, so that it raises the price no more than necessary.
15. As a Bidder, I want my Proxy Bidder to skip when I already lead, so that it never bids against me.
16. As a Bidder, I want my Proxy Bidder to skip any amount above my Budget, so that the Budget remains an absolute safety boundary.
17. As a Bidder, I want an amount equal to my Budget allowed, so that my full authorized range can be used.
18. As a Bidder, I want my Proxy Bidder to stop when the Auction Closes, so that it cannot submit late Bids.
19. As a Bidder, I want a paused or completed Proxy Bidder to ignore new Bid events, so that its status is respected.
20. As a Bidder, I want duplicate RabbitMQ deliveries handled once, so that one accepted Bid cannot cause repeated agent Bids.
21. As a Bidder, I want transient network failures retried only a bounded number of times, so that temporary outages do not create infinite retry loops.
22. As a Bidder, I want retries to reuse the same deterministic reaction identifier, so that a successful request cannot become a duplicate Bid.
23. As a Bidder, I want a stale or losing agent Bid balked, so that the Proxy Bidder does not retry against outdated state.
24. As a Bidder, I want the human and Proxy Bidder paths to share the same Auction validation, so that neither path gains different bidding privileges.
25. As a Seller, I want Proxy Bidders blocked from bidding on their own Seller's Auction, so that the existing anti-shill rule still applies.
26. As a competing Bidder, I want simultaneous Proxy Bidders resolved by the Auction's optimistic lock, so that exactly one Bid becomes the current leader.
27. As an operator, I want each reaction recorded with its source event, proposed amount, attempt count, and outcome, so that autonomous behavior can be explained.
28. As an operator, I want authorization failures treated as configuration errors rather than retried forever, so that broken service credentials are visible.
29. As an operator, I want an Auction-not-found or not-open result treated as terminal for that reaction, so that poison messages do not loop.
30. As an operator, I want the `agentservice` client authenticated independently from human realm roles, so that the realm keeps only `ADMIN`, `BIDDER`, and `SELLER` as business roles.
31. As a system, I want `agentservice` to consume its own durable queue, so that notification and live-feed consumers do not compete with it for the same message.
32. As a system, I want agent-vs-agent bidding to emerge through normal `bid.placed` events, so that no special agent-only auction loop exists.
33. As a system, I want every accepted agent Bid to publish the normal thin `bid.placed` fact, so that all existing consumers react consistently.
34. As a system, I want `auction.closed` to complete every Proxy Bidder for that Auction idempotently, so that repeated Close facts are harmless.
35. As a future AI-assisted Bidder, I want rules to remain authoritative, so that Gemini advice can never override Budget or Auction constraints.

## Implementation Decisions

- **Primary module:** `agentservice` owns Proxy Bidder configuration, rule evaluation, RabbitMQ consumption, reaction state, and the outbound Auction HTTP adapter.
- **Supporting module:** `auctionservice` adds a service-authenticated bid-state read API and a service-authenticated Bid command API. Both stay inside the existing Auction consistency boundary.
- **No separate bidding logic:** human and agent HTTP entry points delegate to the same Bid placement application service. Seller checks, open-window checks, starting price, minimum increment, optimistic locking, persistence, and event publication remain single-sourced.
- **Authentication:** Keycloak gains a confidential `agentservice` client with service accounts enabled and the correct `auctionservice` audience. The internal Auction APIs accept only that client identity. No fourth realm business role is added.
- **Human identity:** Proxy Bidder management APIs derive `bidderId` from the human JWT subject. They never accept a human `bidderId` from a public request body.
- **Trusted delegation:** the internal agent Bid command carries `bidderId` only after `agentservice` has authenticated the owner and loaded the persisted Proxy Bidder. `auctionservice` trusts it only because the caller is the authenticated `agentservice` client.
- **Management API:** authenticated Bidders can create, read, update Budget, pause, and reactivate their own Proxy Bidders. Records are retained for audit rather than hard-deleted.
- **Ownership:** every read or mutation is scoped by both Proxy Bidder ID and JWT subject. A missing or foreign record returns not-found to avoid leaking its existence.
- **Proxy Bidder state:** each record contains an ID, Auction ID, Bidder ID, Budget, status, optimistic version, creation time, and update time.
- **Statuses:** `ACTIVE` reacts to events, `PAUSED` preserves configuration without reacting, and `COMPLETED` is terminal after Auction Close.
- **Uniqueness:** at most one Proxy Bidder exists for a `(auctionId, bidderId)` pair.
- **Budget:** Budget is a positive monetary value and must be at least the Auction starting price when created. It is a hard inclusive maximum; a proposed amount equal to Budget is permitted, while any greater amount is skipped.
- **V1 strategy:** the only strategy is rules-first minimum increment. Proposed amount equals fresh `currentPrice + minIncrement`. Strategy interfaces may provide the seam for Gemini later, but V1 has one concrete rules implementation and no abstract factory.
- **Fresh state:** before each reaction, `agentservice` obtains authoritative current price, minimum increment, status, end time, and highest Bidder from an internal Auction bid-state API. Rabbit event price is a trigger and audit fact, not the decision source of truth.
- **RabbitMQ:** `agentservice` owns a durable queue bound to `bid.placed` and a durable queue bound to `auction.closed` on the existing `auction.events` topic exchange.
- **Own-lead balking:** a reaction is skipped when the fresh highest Bidder equals the Proxy Bidder owner. Checking only the triggering event is insufficient because events can arrive late.
- **Auction status:** a Proxy Bidder reacts only while fresh Auction state is open and inside its bidding window. `auctionservice` repeats the authoritative check when receiving the Bid.
- **Agent-vs-agent behavior:** each accepted agent Bid emits the normal `bid.placed` fact. Other Proxy Bidders may react through the same queue and rules. No direct agent-to-agent communication exists.
- **Concurrency:** simultaneous agent commands are intentionally allowed to race at `auctionservice`; its existing optimistic lock decides the leader. A `409 Conflict` is a normal balked outcome and is not retried.
- **Reaction idempotency:** each `(proxyBidderId, sourceEventId)` produces at most one durable reaction record. RabbitMQ redelivery finds that record instead of creating a second reaction.
- **Reaction lifecycle:** reaction records capture deterministic ID, source event, Proxy Bidder, proposed amount, attempt count, and outcome such as pending, succeeded, balked, skipped, or failed.
- **HTTP idempotency:** every agent Bid command carries the deterministic reaction ID. `auctionservice` stores it with the resulting Bid or command result and returns the existing result when the same reaction is retried.
- **Retry policy:** network errors and `5xx` responses receive a small bounded retry with backoff using the same reaction ID. `409`, Auction-not-open, Seller-cannot-bid, missing Auction, invalid amount, and authorization errors are terminal for that reaction.
- **Close handling:** `auction.closed` marks all Proxy Bidders for that Auction `COMPLETED` and prevents pending reactions from creating new commands. Repeated Close events are idempotent.
- **No Redis authority:** Postgres stores Proxy Bidder and reaction state. Redis is not used for Budget, deduplication, or bidding decisions.
- **Money:** monetary values use decimal types and preserve Auction currency precision. Floating-point types are forbidden.
- **Events remain thin:** existing `bid.placed` and `auction.closed` contracts remain unchanged. Consumers fetch details through authenticated APIs when they need more context.
- **Observability:** structured logs include Proxy Bidder ID, Auction ID, source event ID, reaction ID, proposed amount, attempt number, and terminal outcome. Budget values must not be exposed through public APIs or WebSocket topics.

## Testing Decisions

- **Good test:** assert behavior visible at service boundaries. Do not assert private method calls, strategy implementation class names, JPA version values, listener container internals, or retry-library internals.
- **Primary seam:** drive the authenticated Proxy Bidder HTTP API, publish facts through real RabbitMQ, persist through real Postgres, and observe requests received by a fake `auctionservice` HTTP boundary.
- **Infrastructure:** use Testcontainers Postgres and RabbitMQ. Use a controllable HTTP stub for Auction bid-state and Bid command responses.
- **Headline test:** create an active Proxy Bidder with a `5000` Budget, return fresh Auction state with current price `4000` and minimum increment `100`, publish a competing `bid.placed` fact, and assert exactly one agent command for `4100`. Publish the same event again and assert no second command.
- **Budget boundary:** proposed amount equal to Budget is sent; proposed amount above Budget is skipped with no Auction command.
- **Fresh-state behavior:** when event amount is stale but Auction state is newer, the proposed amount uses the fresh Auction current price.
- **Own-lead behavior:** when fresh Auction state already names the owner as highest Bidder, no command is sent.
- **State behavior:** paused and completed Proxy Bidders ignore `bid.placed`; reactivated Proxy Bidders react again while Auction is open.
- **Close behavior:** `auction.closed` completes all matching Proxy Bidders once; duplicate Close delivery leaves the same result.
- **Ownership behavior:** unauthenticated creation returns `401`; a non-Bidder returns `403`; one Bidder cannot read or mutate another Bidder's Proxy Bidder.
- **Validation behavior:** non-positive Budget, Budget below starting price, malformed input, and duplicate `(auctionId, bidderId)` creation return stable client errors.
- **Balking behavior:** an Auction `409` produces one terminal balked reaction and no retry.
- **Retry behavior:** a transient network or `5xx` failure retries only up to the configured bound, keeps one reaction ID, and cannot create more than one accepted Bid.
- **Authorization failure:** invalid service credentials become a terminal failed reaction and do not loop through RabbitMQ indefinitely.
- **Concurrent agents:** two active Proxy Bidders reacting to the same Auction may both issue commands, while the Auction boundary accepts only the command that wins its optimistic-lock race.
- **Contract compatibility:** verify the agent Bid command and bid-state response against `auctionservice` contracts without duplicating Auction business-rule assertions inside `agentservice`.
- **Prior art:** follow the external-behavior and Testcontainers direction established by the auction Bid core spec. No mature Proxy Bidder test suite exists yet.

## Out of Scope

- Gemini or any other AI advice.
- Strategy selection beyond rules-first minimum increment.
- Dutch or other non-English Auction types.
- Human Bid API redesign.
- Auction creation, scheduling, or Close orchestration.
- Notification delivery, WebSocket broadcasting, Angular UI, or mobile UI.
- Contact Reveal, Chat Thread creation, payment, or off-platform settlement details.
- Proxy Bidder bidding before Auction start.
- A Proxy Bidder spread across multiple Auctions.
- Sharing one Proxy Bidder between multiple human Bidders.
- Redis-based Budget enforcement, locks, or authoritative price state.
- Unbounded retries or automatic retry after a normal `409` balk.
- Hard deletion of Proxy Bidder audit history.

## Further Notes

- `agentservice` already has a Spring Initializr archive using Java 21, Spring Boot 4.1, JPA, Postgres, RabbitMQ, validation, OAuth2 resource server, Web MVC, actuator, Eureka, and matching Testcontainers dependencies.
- The existing Keycloak realm currently has only the three intended business roles: `ADMIN`, `BIDDER`, and `SELLER`. Service authentication should use a confidential client identity rather than adding a fourth human-facing realm role.
- This spec intentionally refines “same Bid API” into “same Bid placement rule engine and transaction boundary.” A separate internal transport entry point keeps service delegation explicit without duplicating bidding behavior.
- Rules always enforce Budget and Auction constraints. Future Gemini advice may suggest whether to bid, but it may never select an amount above the rules-produced maximum or bypass a balk.
- Coding remains developer-owned; this document defines behavior and seams for the next implementation phase.
