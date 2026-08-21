# Derive OPEN Status for Bid Acceptance

Status: done

## Parent

`../spec.md`

## What to build

Fix a live defect: `AuctionService.placeBid` requires `status == OPEN` literally. An Auction created `SCHEDULED` with a future `startAt` never transitions, so once `startAt` passes it is still rejected forever. Replace the literal status check with a derived-effective-status check (time window plus not-CLOSED/not-CANCELLED), so a Bid is accepted once the window opens without any background job.

No scheduler is introduced. `endAt`/`startAt` window checks already independently exist in `placeBid` - this ticket makes the `status` check agree with them instead of contradicting them.

## User stories covered

- Stories 13, 33.

## Acceptance criteria

- [x] An Auction seeded `SCHEDULED` with a past `startAt` and future `endAt` accepts a Bid.
- [x] A CLOSED Auction still rejects a Bid regardless of window.
- [ ] A CANCELLED Auction still rejects a Bid regardless of window (see ticket 06 — status does not exist yet).
- [x] `GET /api/auctions/{id}` (ticket 01) reports the same effective status Bid acceptance uses - the two must never disagree.
- [x] No existing bid-core test regresses.

## Blocked by

- `01-public-browse-and-read.md` (read side asserts the same derived status)
