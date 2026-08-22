# Seller Constrained Edit

Status: done

## Parent

`../spec.md`

## What to build

`PATCH /api/auctions/{auctionId}`, owner-only. Title editable any time before CLOSED/CANCELLED. Starting price and minimum increment editable only while no Bid exists. `endAt` may only extend, never shorten. Concurrent edits use the existing `@Version`.

## User stories covered

- Stories 16-20, 26-31.

## Acceptance criteria

- [x] Title edit succeeds with Bids present.
- [x] Starting price edit rejected (422) once a Bid exists.
- [x] `endAt` extension succeeds; `endAt` shortening rejected (422).
- [x] Edit on a CLOSED Auction rejected (422) — also proven for CANCELLED (ticket 06), same effectiveStatus-derived gate.
- [x] Foreign Seller's edit returns 404, Auction unchanged.
- [x] Two concurrent edits: one succeeds, one 409 (existing `@Version` optimistic lock).
- [x] Wrong role returns 403; unauthenticated returns 401.

## Blocked by

- `03-derive-open-status.md`
- `04-mine-listings.md`
