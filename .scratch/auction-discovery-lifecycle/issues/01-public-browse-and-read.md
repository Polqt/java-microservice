# Public Browse and Read

Status: done

## Parent

`../spec.md`

## What to build

`GET /api/auctions` (paged, defaults to OPEN, optional status filter) and `GET /api/auctions/{auctionId}`. Unauthenticated. Page size bounded server-side regardless of what the client requests.

## User stories covered

- Stories 1-4, 9-12, 32.

## Acceptance criteria

- [x] Browsing defaults to OPEN Auctions only.
- [x] An explicit status filter widens the result beyond OPEN.
- [x] A single Auction read returns starting price, current price, minimum increment, status, and window.
- [x] Both endpoints work with no Authorization header.
- [x] A request for more than the maximum page size returns at most the maximum.
- [x] Reading an unknown Auction returns 404.

## Blocked by

None - can start immediately.
