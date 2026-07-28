# Prove Agent-vs-Agent Bidding

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Prove the standout behavior through one full external scenario containing two active Proxy Bidders with different Budgets. Their accepted Bids must trigger each other through RabbitMQ and use normal Auction commands until one reaches its Budget boundary. The Auction's optimistic locking remains the only winner-selection authority.

This ticket adds the highest-value integration evidence and fixes only defects exposed by that scenario.

## User stories covered

- Stories 17, 26, and 32.

## Acceptance criteria

- [ ] Two authenticated Bidders can configure active Proxy Bidders on one open Auction.
- [ ] A competing accepted Bid starts agent-vs-agent reactions through real RabbitMQ delivery.
- [ ] Every accepted agent Bid follows the normal Auction Bid path and emits the normal `bid.placed` fact.
- [ ] Neither Proxy Bidder ever submits an amount above its Budget.
- [ ] An amount equal to Budget remains valid.
- [ ] Duplicate delivery cannot create duplicate accepted Bids.
- [ ] Concurrent reactions resolve through Auction optimistic locking; one command leads and stale competitors balk.
- [ ] Bidding stops when no Proxy Bidder can submit another valid amount.
- [ ] Persisted Auction lead and Bid history match the externally observed accepted results.
- [ ] The scenario exercises real Postgres and RabbitMQ boundaries and records repeatable evidence.

## Blocked by

- `06-idempotent-retry-safe-reactions.md`
- `07-complete-on-auction-close.md`
