# React Once to a Competing Bid

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Deliver the first event-to-Bid tracer bullet. `agentservice` consumes an accepted `bid.placed` fact from its own durable queue, finds active Proxy Bidders for that Auction, reads fresh Auction state, calculates exactly one minimum-increment response within Budget, and submits it through a service-authenticated Auction command.

The internal command delegates to the same Auction Bid placement application service used by humans. Auction rules and optimistic locking remain authoritative. This slice performs one attempt; durable retry and full redelivery hardening belong to the next ticket.

## User stories covered

- Stories 12–18, 24–26, and 31–33.

## Acceptance criteria

- [ ] `agentservice` owns a durable queue bound to `bid.placed` on the existing Auction topic exchange.
- [ ] A competing accepted Bid triggers evaluation of active Proxy Bidders for that Auction.
- [ ] Decision input comes from fresh Auction bid state, not only from the event amount.
- [ ] Proposed amount equals fresh current price plus minimum increment.
- [ ] No command is sent when the owner already leads, the Proxy Bidder is inactive, the Auction is not open, or the proposed amount exceeds Budget.
- [ ] An amount exactly equal to Budget is allowed.
- [ ] The internal Auction command accepts only the authenticated `agentservice` client.
- [ ] The internal command delegates to the same Bid placement logic and produces the normal `bid.placed` fact when accepted.
- [ ] Seller, starting-price, minimum-increment, open-window, and optimistic-lock rules remain enforced by `auctionservice`.
- [ ] A `409 Conflict` is recorded as a normal balk and is not immediately retried.

## Blocked by

- `03-create-owned-proxy-bidder.md`
