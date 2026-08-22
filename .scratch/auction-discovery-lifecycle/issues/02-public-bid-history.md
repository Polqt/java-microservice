# Public Bid History

Status: done

## Parent

`../spec.md`

## What to build

`GET /api/auctions/{auctionId}/bids` - paged Bid history for one Auction. Unauthenticated. Exposes bidder identifier, amount, timestamp only.

## User stories covered

- Stories 5-6.

## Acceptance criteria

- [x] Response contains no field carrying an email, name, or contact detail (BidHistoryItem exposes only bidderId/amount/placedAt).
- [x] Paged, same bound as browsing (MAX_PAGE_SIZE).
- [x] Unknown Auction returns 404.

## Blocked by

None - can start immediately.
