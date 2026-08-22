# Seller Cancel

Status: done

## Parent

`../spec.md`

## What to build

`POST /api/auctions/{auctionId}/cancel`, owner-only. Adds `CANCELLED` to `AuctionStatus`. Permitted only while no Bid exists and status is not already CLOSED. No hard-delete anywhere. A CANCELLED Auction rejects further Bids (extend ticket 03's derived-status check) and remains readable.

## User stories covered

- Stories 21-25.

## Acceptance criteria

- [x] Cancel with no Bids succeeds; status becomes CANCELLED.
- [x] Cancel with Bids present returns 422.
- [x] A CANCELLED Auction rejects a subsequent Bid the same way a CLOSED one does.
- [x] `GET /api/auctions/{id}` still returns a CANCELLED Auction (never deleted).
- [x] Foreign Seller's cancel returns 404.
- [x] No endpoint anywhere issues a hard delete of an Auction or Bid row (no repository `delete*` call added anywhere in this diff).

## Blocked by

- `05-seller-constrained-edit.md`
