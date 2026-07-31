# Spec: Auction Discovery and Seller Lifecycle

Status: ready-for-agent

## Problem Statement

A Bidder cannot find anything to bid on. `auctionservice` exposes no public read of any kind, so the only way to reach the Bid endpoint is to already know an Auction's identifier out of band. A Seller can create an Auction but can never list their own, correct a mistake in one, or withdraw one that should not have been listed.

There is also a latent defect in creation: an Auction created with a future `startAt` is persisted as `SCHEDULED`, and nothing ever moves it to `OPEN`. Because Bid acceptance requires `status == OPEN`, such an Auction can never receive a Bid, even after its start time passes.

## Solution

`auctionservice` gains the public read surface the platform is missing — browse open Auctions, read one Auction, read its Bid history — and the Seller-facing lifecycle operations that let an owner manage what they listed.

Auctions become biddable when their open window arrives, without a Seller or an operator intervening. Seller edits are permitted only where they cannot change the rules of a contest already underway, and an unwanted Auction is withdrawn rather than destroyed, so Bid history is never erased.

## User Stories

1. As a Bidder, I want to browse open Auctions, so that I can find something to bid on without being given an identifier.
2. As a Bidder, I want to read a single Auction, so that I can see its current price and terms before bidding.
3. As a Bidder, I want to see an Auction's minimum increment and starting price, so that I know what a valid Bid is.
4. As a Bidder, I want to see when an Auction closes, so that I can judge how much time remains.
5. As a Bidder, I want to see an Auction's Bid history, so that I can judge the level of competition.
6. As a Bidder, I want the Bid history to identify Bidders without exposing personal details, so that competing does not disclose who I am.
7. As a Bidder, I want to list the Auctions I have bid on, so that I can track what I am winning or losing.
8. As a Bidder, I want to see whether I currently hold the lead on an Auction, so that I know whether to bid again.
9. As a Bidder, I want closed Auctions excluded from browsing by default, so that I do not waste time on finished contests.
10. As a Bidder, I want to filter browsing by status, so that I can deliberately look at finished Auctions.
11. As a Bidder, I want browsing to be paged, so that a large catalogue stays usable.
12. As a Bidder, I want browsing available without signing in, so that I can evaluate the platform before registering.
13. As a Seller, I want an Auction to become biddable when its start time arrives, so that scheduling an Auction in advance actually works.
14. As a Seller, I want to list the Auctions I own, so that I can manage them.
15. As a Seller, I want my own listing to include Auctions in every status, so that I can see scheduled, open, closed, and cancelled work in one place.
16. As a Seller, I want to correct an Auction's title before bidding begins, so that a typo is not permanent.
17. As a Seller, I want to correct price terms only before any Bid exists, so that I cannot be accused of changing the rules mid-contest.
18. As a Seller, I want price changes rejected once a Bid exists, so that Bidders can trust the terms they bid under.
19. As a Seller, I want to extend an Auction's end time, so that a slow-starting Auction can attract more Bidders.
20. As a Seller, I want shortening an Auction's end time rejected, so that I cannot cut off a Bidder who is about to win.
21. As a Seller, I want to cancel an Auction that has received no Bids, so that a mistaken listing can be withdrawn.
22. As a Seller, I want cancelling an Auction that has Bids rejected, so that I cannot strand Bidders who relied on it.
23. As a Seller, I want no hard-delete operation to exist, so that Auction and Bid history remains auditable.
24. As a Seller, I want a cancelled Auction to reject further Bids, so that withdrawal is final.
25. As a Seller, I want a cancelled Auction to remain readable, so that its history is preserved.
26. As a Seller, I want another Seller's Auction to be unmodifiable by me, so that ownership is enforced.
27. As a Seller, I want a foreign Auction's mutation attempt to report not-found, so that ownership checks do not confirm what exists.
28. As a Seller, I want a closed Auction to be immutable, so that settled outcomes cannot be rewritten.
29. As a Seller, I want concurrent edits to my Auction to conflict rather than overwrite, so that no change is silently lost.
30. As an unauthenticated visitor, I want Seller operations rejected, so that only signed-in owners can manage listings.
31. As a Bidder, I want Seller-only operations rejected for my role, so that role separation is enforced.
32. As an operator, I want reads to avoid loading unbounded result sets, so that one request cannot exhaust the service.
33. As a system, I want Auction status to remain consistent with its open window, so that status and time can never disagree.
34. As a system, I want Bid placement rules to remain unchanged by this work, so that the existing concurrency guarantees still hold.

## Implementation Decisions

- **Module:** all work in `auctionservice`. No new service, and no change to Bid placement, Close, or Deal creation logic.
- **Public read endpoints** are added under the existing `/api/auctions` path and are routed through the gateway. Browsing and single-Auction reads are unauthenticated; everything that names "mine" requires a token.
- **Browse contract:** a paged list of Auctions, defaulting to `OPEN` only, with an explicit status filter to widen it. Page size is bounded server-side regardless of what the client requests.
- **Bid history contract:** a paged list of Bids for one Auction, exposing bidder identifier, amount, and timestamp. No Bidder contact details, email, or name — those belong to `userservice` and are only revealed through a Deal.
- **`SCHEDULED → OPEN` is derived, not scheduled.** A read computes effective status from `status` plus the open window rather than depending on a background job flipping rows. Bid acceptance already validates the window independently, so no scheduler is introduced and the two can never disagree.
- **`CANCELLED` is added to the Auction status enum.** Cancellation sets the status; no row is ever deleted. Bid acceptance rejects `CANCELLED` exactly as it rejects `CLOSED`.
- **Edit rules are split by whether a contest is underway:**
  - Title is editable while the Auction is not `CLOSED` or `CANCELLED`.
  - Starting price and minimum increment are editable only while no Bid exists.
  - `endAt` may be extended at any time before Close; any change that moves it earlier is rejected.
  - `startAt` is editable only while no Bid exists and the Auction has not started.
- **Cancellation rule:** permitted only while the Auction has no Bids and is not already `CLOSED`. Cancelling an Auction with Bids is refused rather than compensated, because the platform has no mechanism to make stranded Bidders whole.
- **Ownership failures report not-found, not forbidden**, matching the rule the Proxy Bidder tickets already use — a forbidden response would confirm the resource exists to someone with no right to know.
- **Concurrency:** Seller mutations reuse the Auction's existing `@Version`. A stale edit conflicts rather than overwriting, consistent with ADR-0002.
- **API contract:**
  - `GET /api/auctions` — paged browse, optional status filter, unauthenticated.
  - `GET /api/auctions/{auctionId}` — single Auction, unauthenticated, `404` when unknown.
  - `GET /api/auctions/{auctionId}/bids` — paged Bid history, unauthenticated.
  - `GET /api/auctions/mine` — Seller's own Auctions, all statuses, `SELLER` role.
  - `GET /api/bids/mine` — Bidder's own Bids with current lead status, `BIDDER` role.
  - `PATCH /api/auctions/{auctionId}` — constrained edit, owner only.
  - `POST /api/auctions/{auctionId}/cancel` — withdraw, owner only.
- **Status codes:** `200` for reads and successful mutations, `401` unauthenticated, `403` wrong role, `404` unknown or not owned, `409` stale concurrent edit, `422` rule violation (price change after a Bid, `endAt` moved earlier, cancel with Bids).
- **Read models never expose entities.** `version`, and any future internal field, stay out of response records.
- **No Redis.** Postgres remains the source of truth for reads. A cache is added only when a measured read problem exists, per the Bid core spec's position.

## Testing Decisions

- **Good test:** drive the HTTP API and assert responses plus persisted state. Do not assert on repository calls, `@Version` numbers, derived-status helper methods, or Spring bean wiring.
- **Seam:** the existing `auctionservice` HTTP API, exercised with Testcontainers Postgres — the same seam and harness as `PlaceBidApiTest`. No new seam is introduced.
- **Prior art:** `PlaceBidApiTest` establishes the pattern — `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`, forged JWTs via `SecurityMockMvcRequestPostProcessors.jwt()` carrying `ROLE_BIDDER` or `ROLE_SELLER`, Auctions seeded through the repository.
- **Derived status is proven through behaviour, not the helper:** seed an Auction whose `startAt` has passed while its stored status is `SCHEDULED`, then assert a Bid is accepted and the read reports it open. Asserting the helper directly would test the implementation.
- **Edit rules each get a test:** title editable with Bids present; starting price rejected with Bids present; `endAt` extension accepted; `endAt` shortening rejected; closed Auction immutable.
- **Cancellation:** cancel with no Bids succeeds and subsequent Bids are rejected; cancel with Bids returns `422`; no endpoint deletes a row.
- **Ownership:** a foreign Seller's `PATCH` and `cancel` both return `404`, and the Auction is unchanged afterwards.
- **Concurrency:** two concurrent `PATCH` requests against one Auction produce exactly one success and one `409`, mirroring the concurrent-bid test's shape.
- **Paging:** a request for more than the maximum page size returns at most the maximum.
- **Privacy:** the Bid history response contains no field carrying an email, name, or contact detail.

## Out of Scope

- Contact Reveal and Deal retrieval — separate spec, because the authorization and cross-service concerns are materially different.
- Search by keyword, category, price range, or any ranking beyond simple status filtering and ordering.
- Images, attachments, or any Item media.
- Watchlists, favourites, or Bidder notifications about Auctions they are not bidding on.
- A background scheduler for status transitions or automatic Close at `endAt`.
- Redis caching of the browse or read paths.
- Any change to Bid placement, Close, Deal creation, or the Proxy Bidder flow.
- Seller reputation, ratings, or history aggregates.

## Further Notes

- The `SCHEDULED → OPEN` gap is a live defect introduced with Auction creation, not a new feature: an Auction with a future `startAt` is currently unbiddable forever. Deriving status fixes it without adding a scheduler, and keeps status and window from ever disagreeing.
- Refusing to cancel an Auction that has Bids is a deliberate simplification. The alternative — cancelling and compensating Bidders — needs a notification path and a policy the platform does not yet have.
- Adding `CANCELLED` changes an enum persisted as a string. With `ddl-auto` this is invisible, but it is exactly the kind of change that argues for Flyway before the schema matters.
- Glossary terms are defined in `CONTEXT.md`: Auction, Bid, Bidder, Seller, Close. Use them; avoid "listing", "offer", "buyer".
