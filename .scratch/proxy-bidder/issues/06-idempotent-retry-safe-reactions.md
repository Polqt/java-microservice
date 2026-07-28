# Make Reactions Duplicate-Safe and Retry-Safe

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Harden the event-to-Bid path against RabbitMQ redelivery, process restarts, and uncertain HTTP outcomes. Each Proxy Bidder and source event pair must create one durable reaction with a deterministic identifier. Retried Auction commands reuse that identifier, and `auctionservice` returns the existing result instead of creating another Bid.

Only transient network and server failures receive bounded backoff. Normal balks and permanent client or authorization failures are terminal and must not create a poison-message loop.

## User stories covered

- Stories 20–23 and 27–29.

## Acceptance criteria

- [ ] `(proxyBidderId, sourceEventId)` is unique and identifies one durable reaction.
- [ ] Reaction state records source event, proposed amount, attempts, deterministic reaction ID, and terminal outcome.
- [ ] Duplicate `bid.placed` delivery cannot create a second Auction command for the same reaction.
- [ ] Every retry sends the same deterministic reaction ID.
- [ ] `auctionservice` handles a repeated reaction ID idempotently and cannot persist a second accepted Bid.
- [ ] Network failures and `5xx` responses retry only up to the configured bound with backoff.
- [ ] `409`, not-open, Seller-cannot-bid, missing Auction, invalid amount, and authorization failures are terminal.
- [ ] A process restart can resume a pending transient reaction without losing its identifier.
- [ ] Structured logs expose reaction ID, source event ID, attempt, proposed amount, and outcome.
- [ ] Two Proxy Bidders may race while `auctionservice` still accepts only the optimistic-lock winner.

## Blocked by

- `05-react-to-competing-bid.md`
