# Seller and Bidder "Mine" Listings

Status: done

## Parent

`../spec.md`

## What to build

`GET /api/auctions/mine` (SELLER role, all statuses) and `GET /api/bids/mine` (BIDDER role, includes current-lead flag per Auction).

## User stories covered

- Stories 7-8, 14-15.

## Acceptance criteria

- [x] Seller's own listing includes SCHEDULED, OPEN, CLOSED (CANCELLED added in ticket 06 and covered by the same query — no status filtering at all).
- [x] Bidder's own Bid list indicates whether they currently lead each Auction.
- [x] Wrong role on either endpoint returns 403.
- [x] Unauthenticated returns 401.

## Blocked by

- `01-public-browse-and-read.md`
- `02-public-bid-history.md`
