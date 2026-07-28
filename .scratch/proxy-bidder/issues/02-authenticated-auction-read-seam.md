# Establish Authenticated Agent-to-Auction Read Seam

Status: ready-for-agent

## Parent

`../spec.md`

## What to build

Create the trusted service-to-service read seam that a Proxy Bidder will use before deciding. Keycloak must issue a client-credentials token to a confidential `agentservice` client. `auctionservice` must expose current authoritative bid state only to that client identity, without adding another human realm role.

The response supplies current price, starting price, minimum increment, Auction status, open-window timestamps, and current highest Bidder. It exposes only the state required for agent decisions.

## User stories covered

- Stories 13 and 30.

## Acceptance criteria

- [ ] Keycloak contains a confidential `agentservice` client with service accounts enabled.
- [ ] The service token has the intended `auctionservice` audience and cannot be obtained without the client secret.
- [ ] The internal Auction bid-state endpoint returns authoritative decision fields for an existing Auction.
- [ ] An authenticated `agentservice` token can read bid state.
- [ ] A missing token, human token, or token from another client is rejected.
- [ ] A missing Auction returns not-found without leaking unrelated state.
- [ ] No fourth human-facing realm role is introduced.

## Blocked by

None - can start immediately.
