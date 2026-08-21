# Prove Agent-vs-Agent Bidding

Status: done

## Parent

`../spec.md`

## What to build

Prove the standout behavior through one full external scenario containing two active Proxy Bidders with different Budgets. Their accepted Bids must trigger each other through RabbitMQ and use normal Auction commands until one reaches its Budget boundary. The Auction's optimistic locking remains the only winner-selection authority.

This ticket adds the highest-value integration evidence and fixes only defects exposed by that scenario.

## User stories covered

- Stories 17, 26, and 32.

## Acceptance criteria

- [x] Two authenticated Bidders can configure active Proxy Bidders on one open Auction.
- [x] A competing accepted Bid starts agent-vs-agent reactions through real RabbitMQ delivery.
- [x] Every accepted agent Bid follows the normal Auction Bid path and emits the normal `bid.placed` fact.
- [x] Neither Proxy Bidder ever submits an amount above its Budget.
- [x] An amount equal to Budget remains valid.
- [x] Duplicate delivery cannot create duplicate accepted Bids.
- [x] Concurrent reactions resolve through Auction optimistic locking; one command leads and stale competitors balk (proven at the agentservice boundary — a lost race surfaces as 409 and is marked BALKED, terminal, not retried; auctionservice's own concurrency is separately proven by its own PlaceBidApiTest).
- [x] Bidding stops when no Proxy Bidder can submit another valid amount.
- [x] Persisted Auction lead and Bid history match the externally observed accepted results (proven against the fake Auction server standing in for auctionservice — see AgentVsAgentBiddingTest).
- [x] The scenario exercises real Postgres and RabbitMQ boundaries and records repeatable evidence (5/5 clean runs).

## Blocked by

- `06-idempotent-retry-safe-reactions.md`
- `07-complete-on-auction-close.md`
