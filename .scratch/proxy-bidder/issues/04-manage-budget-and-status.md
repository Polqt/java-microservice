# Manage Proxy Bidder Budget and Status

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Let an owning Bidder change Budget and move a Proxy Bidder between `ACTIVE` and `PAUSED`. Updates must preserve the hard Budget invariant, respect Auction state, and reject stale concurrent edits through optimistic locking. Completed records remain immutable audit history.

## User stories covered

- Stories 6–8 and 19.

## Acceptance criteria

- [ ] The owner can pause an active Proxy Bidder.
- [ ] The owner can reactivate a paused Proxy Bidder only while its Auction is open.
- [ ] The owner can change Budget to a positive value that remains valid for the Auction.
- [ ] A Budget below the Auction starting price is rejected.
- [ ] A foreign Bidder receives not-found for every attempted mutation.
- [ ] A completed Proxy Bidder cannot be reactivated or modified.
- [ ] Concurrent stale updates produce a conflict rather than silently overwriting state.
- [ ] No hard-delete operation is exposed.

## Blocked by

- `03-create-owned-proxy-bidder.md`
