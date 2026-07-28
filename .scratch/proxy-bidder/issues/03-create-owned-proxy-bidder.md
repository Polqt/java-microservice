# Create and Inspect Owned Proxy Bidder

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Deliver the first complete Bidder-facing Proxy Bidder path. An authenticated Bidder creates one Proxy Bidder for one Auction, with identity taken from the JWT subject. `agentservice` reads Auction state through the trusted seam, validates the Budget, persists the record, and lets only its owner retrieve it.

New Proxy Bidders start active. Their Budget remains private and is never published through public events or WebSocket topics.

## User stories covered

- Stories 1–5 and 9–11.

## Acceptance criteria

- [ ] An authenticated Bidder can create a Proxy Bidder using an Auction ID and Budget.
- [ ] `bidderId` comes from the JWT subject and is not accepted from the public body.
- [ ] Budget must be positive and at least the Auction starting price.
- [ ] A newly created Proxy Bidder is `ACTIVE`.
- [ ] The persisted record contains ID, Auction ID, Bidder ID, Budget, status, optimistic version, and timestamps.
- [ ] A `(auctionId, bidderId)` pair is unique and duplicate creation returns a stable conflict response.
- [ ] The owner can retrieve the Proxy Bidder.
- [ ] A foreign Bidder receives not-found when attempting to retrieve it.
- [ ] Unauthenticated and non-Bidder callers are rejected.

## Blocked by

- `01-run-agentservice-skeleton.md`
- `02-authenticated-auction-read-seam.md`
