# Auction Platform

A real-time, Philippines-focused online auction. Sellers list items; bidders (human or automated) compete in live ascending auctions. When an auction closes, the platform introduces winner and seller, who settle the exchange themselves off-platform.

## Language

**Auction**:
A time-bound ascending (English) sale of a single Item, won by the highest Bid at close.
_Avoid_: Listing, sale, lot

**Item**:
The single good being auctioned. Belongs to exactly one Auction.
_Avoid_: Product, lot, good

**Bid**:
An offer of a price on an Auction by a Bidder. Accepted only if it beats the current highest Bid; a losing/stale Bid is rejected outright, never queued.
_Avoid_: Offer, quote

**Bidder**:
A participant placing Bids on an Auction. May be a human or a Proxy Bidder.
_Avoid_: Buyer (a Bidder only becomes a buyer conceptually after winning), customer

**Seller**:
The User who owns the Item and the Auction.
_Avoid_: Vendor, merchant, owner

**Proxy Bidder**:
An autonomous agent that places Bids on a Bidder's behalf up to a budget, reacting to other Bids in real time. Rules-driven, with an optional advisory judgement call.
_Avoid_: Bot, auto-bidder, robot

**Budget**:
The hard maximum a Proxy Bidder may ever bid. Never exceeded, regardless of advice.
_Avoid_: Limit, cap (ambiguous), max price

**Close**:
The moment an Auction ends and its winner is determined exactly once.
_Avoid_: End, finish, expire

**Deal**:
The record created at Close pairing the winning Bidder with the Seller. Grants both parties access to each other's contact details.
_Avoid_: Order, transaction, purchase, sale

**Contact Reveal**:
Exposing Seller and winning Bidder contact details to each other (and only to them) once a Deal exists, so they can arrange the exchange off-platform.
_Avoid_: Handoff, connect

**Chat Thread**:
A one-to-one text conversation between the two parties of a Deal, available only after Close.
_Avoid_: Message, DM, room, channel
