# Spec: Deal Retrieval and Contact Reveal

Status: ready-for-agent

## Problem Statement

When an Auction Closes, `auctionservice` records a Deal pairing the winning Bidder with the Seller — and nothing ever reads it. No endpoint returns a Deal, and no party is told who the other one is.

This is the platform's terminal step. ADR-0004 states the platform handles no money and instead introduces the two parties so they can settle off-platform. Without Contact Reveal, a won Auction produces a database row and nothing else: the winner does not know who to pay, and the Seller does not know who to hand the Item to.

## Solution

`auctionservice` exposes the Deals a caller is party to, and reveals each party's contact details to the other — and to nobody else. Contact details live in `userservice`, so `auctionservice` authorizes the request against the Deal it owns, then fetches contacts over the internal service-authenticated seam already established between services.

A caller who is neither the Seller nor the winning Bidder is told the Deal does not exist, rather than that they may not see it.

## User Stories

1. As a winning Bidder, I want to see the Deal I won, so that I know the Auction concluded in my favour.
2. As a winning Bidder, I want the Seller's contact details, so that I can arrange payment and collection.
3. As a Seller, I want the winning Bidder's contact details, so that I can arrange handover.
4. As a Seller, I want to list the Deals from my Auctions, so that I can track what I still owe delivery on.
5. As a Bidder, I want to list the Deals I won, so that I can track what I still owe payment on.
6. As either party, I want the final price on the Deal, so that we agree what was owed.
7. As either party, I want the Auction and winning Bid identifiers on the Deal, so that the outcome is traceable.
8. As either party, I want the Deal's creation time, so that I know when the obligation began.
9. As either party, I want deep links to message the other party on an external platform, so that we can continue off-platform.
10. As a user who is not party to a Deal, I want it reported as not found, so that the response does not confirm it exists.
11. As an unauthenticated visitor, I want Deal endpoints rejected, so that contact details are never public.
12. As a Seller, I want contacts revealed only after Close, so that Bidders cannot harvest my details by browsing.
13. As a Bidder, I want my contact details withheld from every losing Bidder, so that competing does not expose me.
14. As a Bidder, I want my contact details withheld from Sellers whose Auctions I did not win, so that bidding is not a disclosure.
15. As either party, I want only the contact fields needed to arrange handover, so that unrelated profile data stays private.
16. As either party, I want the reveal to work even if the other party later edits their profile, so that I always see current details.
17. As a system, I want the Deal to remain the single authority on who may see what, so that authorization is not duplicated across services.
18. As a system, I want `userservice` to expose contacts only to authenticated services, so that no client can read them directly.
19. As a system, I want the internal contact endpoint kept off the public gateway, so that it is unreachable from a browser.
20. As a system, I want a missing counterpart profile to degrade gracefully, so that one absent user does not break the whole Deal view.
21. As a system, I want no contact details persisted in `auctionservice`, so that personal data has one owner.
22. As a system, I want no contact details published on any event, so that consumers cannot accumulate personal data.
23. As an operator, I want contact reveals recorded in logs by identifier only, so that access is auditable without logging the details themselves.
24. As an operator, I want contact details excluded from logs entirely, so that a log dump is not a data breach.
25. As an operator, I want the internal contact call bounded by a timeout, so that a slow `userservice` cannot hang the Deal view.
26. As an operator, I want a `userservice` outage to yield a clear degraded response, so that it is distinguishable from an authorization failure.
27. As a security engineer, I want the party check performed against persisted Deal state, so that a forged request body cannot grant access.
28. As a security engineer, I want the caller's identity taken from the token, so that a caller cannot claim to be another party.
29. As a security engineer, I want the internal contact endpoint to reject human tokens, so that a Bidder cannot call it directly.
30. As a future maintainer, I want Chat Thread eligibility derivable from the Deal, so that `chatservice` can authorize a thread without duplicating the party rule.

## Implementation Decisions

- **Modules:** `auctionservice` gains Deal read endpoints and an internal client; `userservice` gains one internal contact endpoint. No new service.
- **Authorization lives with the Deal.** `auctionservice` owns the Deal and is therefore the only place that can answer "is this caller a party". `userservice` is never asked to make that judgement — it only answers "give me this user's contact details" to an authenticated service.
- **Reveal is a runtime join, not a snapshot.** `auctionservice` stores no contact details and fetches them per request. This keeps personal data owned by exactly one service, means profile edits are reflected immediately, and avoids stale copies that would have to be deleted on account closure. Cost: an extra internal call on the Deal read path, and a dependency on `userservice` availability.
- **Internal seam mirrors the existing agent pattern.** `userservice` exposes a service-authenticated contact endpoint under an `/internal` path, kept off the gateway, authorized by confidential client identity and audience rather than a human realm role — the same shape as the `agentservice` → `auctionservice` seam, and consistent with the ai-advisor spec's position that no further human realm role is added.
- **Contact projection is minimal:** display name and the contact handles needed to arrange handover. Not the full profile, not internal identifiers, not audit timestamps.
- **Not-found over forbidden.** A caller who is not a party receives `404`. This matches the Proxy Bidder ownership rule and avoids confirming the Deal's existence.
- **Degraded reveal.** If `userservice` is unavailable or the counterpart profile is missing, the Deal itself is still returned with contacts absent and a flag indicating the reveal is unavailable. A Deal is a settled fact; a contact lookup is a lookup. Failing the whole response would make a transient dependency outage look like the Deal was lost.
- **Deep links are derived client-side** from revealed handles. The platform stores handles, not URLs, so it does not have to track external platforms' URL formats.
- **API contract:**
  - `GET /api/deals/mine` — Deals where the caller is Seller or winning Bidder, paged.
  - `GET /api/deals/{dealId}` — one Deal with both parties' contacts revealed, `404` to non-parties.
  - `GET /internal/users/{userId}/contact` — `userservice`, service-authenticated, off-gateway.
- **Status codes:** `200` on success including degraded reveal, `401` unauthenticated, `404` unknown Deal or caller not a party, `403` on the internal endpoint for a human or wrong-client token.
- **Events carry no contact details.** `deal.created` keeps its current identifier-only payload. Consumers needing contacts must read the Deal, subject to the same authorization.
- **Logging:** record deal identifier, caller identifier, and reveal outcome. Never log names, emails, phone numbers, or handles.
- **Timeout:** the internal contact call is short and bounded, and is not retried on the read path — a degraded response is preferable to a slow one.

## Testing Decisions

- **Good test:** drive the HTTP API as each party and assert what is and is not present in the response body. Do not assert on the internal client's method calls, HTTP interceptors, or Spring wiring.
- **Primary seam:** the `auctionservice` Deal HTTP API with Testcontainers Postgres, stubbing the `userservice` contact client at its boundary. Same seam and harness as `PlaceBidApiTest`.
- **Secondary seam:** `userservice`'s internal contact endpoint, driven directly to assert its service-authentication behaviour.
- **Prior art:** `PlaceBidApiTest` for the harness; `ProxyBidderReactionServiceTest` for stubbing an outbound service client at the module boundary.
- **The privacy tests are the point of this spec:**
  - Winning Bidder reads the Deal and receives the Seller's contacts.
  - Seller reads the Deal and receives the winning Bidder's contacts.
  - A losing Bidder receives `404`.
  - An unrelated authenticated user receives `404`.
  - An unauthenticated caller receives `401`.
  - No response on any Auction or Bid endpoint contains a contact field.
- **Degradation:** with the contact client failing, the Deal is still returned, contacts are absent, and the response is distinguishable from an authorization failure.
- **Internal endpoint security:** a human token returns `403`; a token from another service client returns `403`; the authenticated `auctionservice` client succeeds; no token returns `401`.
- **Log assertion is behavioural where practical:** the reveal path emits identifiers; contact values must not appear. Where asserting log content is impractical, this is enforced by review rather than a brittle test.

## Out of Scope

- In-app messaging. `chatservice` owns Chat Threads; this spec only makes Deal party membership readable so that eligibility can be derived.
- Any payment, escrow, invoice, or settlement tracking — ADR-0004 places money outside the platform.
- Marking a Deal complete, disputed, cancelled, or rated. A Deal is currently a record of outcome, not a workflow.
- Notifying either party that a Deal exists — `notificationservice` consumes `deal.created` and is specified separately.
- Contact verification, phone or email confirmation, or anti-fraud checks on handles.
- Profile management. `userservice` owns how contact details are set; this spec only reads them.
- Rate limiting or abuse detection on the reveal endpoint.
- Data-deletion and retention policy for contact details.

## Further Notes

- This closes the loop ADR-0004 describes. Until it exists, a won Auction produces a Deal row that nobody can act on, and the platform does not actually do the thing it was designed to do.
- The runtime-join decision is worth an ADR: it is hard to reverse once clients depend on live contacts, it is surprising to a reader who might expect a snapshot on a settled record, and the alternative (snapshotting at Close) is a genuine trade-off — immutable and outage-proof, but duplicating personal data that then has to be expired.
- `CONTEXT.md` defines Deal and Contact Reveal. The glossary has no term for the minimal contact projection; if one is needed during implementation, resolve it there rather than inventing a synonym in code.
- Contact Reveal is the most privacy-sensitive surface in the system. The tests that matter here are the negative ones — what a losing Bidder cannot see.
