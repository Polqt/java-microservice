# Complete Proxy Bidders When Auction Closes

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Consume `auction.closed` through an `agentservice` durable queue and move every Proxy Bidder for that Auction to terminal `COMPLETED` state. Repeated Close facts must be harmless, and reactions that have not yet submitted a Bid must no longer proceed after completion.

## User stories covered

- Stories 18–19 and 34.

## Acceptance criteria

- [ ] `agentservice` owns a durable queue bound to `auction.closed`.
- [ ] One Close fact moves all active and paused Proxy Bidders for that Auction to `COMPLETED`.
- [ ] Proxy Bidders for other Auctions remain unchanged.
- [ ] Repeating the same Close fact leaves the same terminal state.
- [ ] Completed Proxy Bidders ignore later `bid.placed` facts.
- [ ] Pending reactions that have not submitted a command are cancelled or skipped before sending.
- [ ] Completed records remain readable by their owners and cannot be reactivated.

## Blocked by

- `03-create-owned-proxy-bidder.md`
