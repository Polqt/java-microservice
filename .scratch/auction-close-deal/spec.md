# Spec: Auction Close and Deal Creation

## Goal

Close an Auction exactly once. If a winning Bid exists, create exactly one Deal between the Seller and winning Bidder.

## Rules

- An Auction can Close only when `now >= endAt`.
- An already Closed Auction returns its existing Close result.
- An Auction without Bids Closes without a Deal.
- An Auction with a highest Bid creates exactly one Deal.
- The Deal final price equals the Auction current price.
- One Auction can have at most one Deal.
- Close and Deal creation happen in one database transaction.

## API

`POST /api/auctions/{auctionId}/close`

- `200 OK`: Auction Closed, or the existing Close result is returned.
- `403 Forbidden`: Close attempted before `endAt`.
- `404 Not Found`: Auction does not exist.
- `409 Conflict`: Concurrent Close conflict.

## Deal Fields

- `id`
- `auctionId`
- `sellerId`
- `winningBidderId`
- `winningBidId`
- `finalPrice`
- `createdAt`

## Events

- `auction.closed`
- `deal.created`

## Out of Scope

- Contact Reveal
- Chat Thread
- Notifications
- Payments
- Auction scheduling
