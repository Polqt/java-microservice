# Implementation Notes — missing pieces, with reasoning

Code for the specified-but-unbuilt behaviour, ordered by what unblocks the most.
Each section states the trade-off taken and the edge cases that drove it, so the
choice can be re-judged later rather than re-derived.

Scope note: this covers the pieces that are buildable now against existing code.
The full `ai-advisor` spec (40 stories) is a build of its own — section 6 gives its
load-bearing seam only, not the whole service.

---

## 1. Auction creation — `auction-bid-core`

**Why first:** nothing in the system can create an Auction. No manual run, no
Insomnia call, no ticket-08 scenario is possible without it. Every other gap is
downstream of this one.

### DTOs

```java
// dto/CreateAuctionRequest.java
public record CreateAuctionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Positive BigDecimal startingPrice,
        @NotNull @Positive BigDecimal minIncrement,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt
) {}
```

```java
// dto/AuctionResponse.java
public record AuctionResponse(
        String id,
        String sellerId,
        String title,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}
```

**Trade-off — `sellerId` is not in the request.** It comes from the JWT subject,
exactly as `bidderId` does on the bid path. Accepting it from the body would let
any authenticated user create an Auction owned by someone else, and the
seller-only close check would then guard nothing.

### Service

```java
@Transactional
public AuctionResponse createAuction(String sellerId, CreateAuctionRequest request) {
    if (!request.endAt().isAfter(request.startAt())) {
        throw new InvalidAuctionWindowException("endAt must be after startAt");
    }

    LocalDateTime now = LocalDateTime.now();
    if (request.endAt().isBefore(now)) {
        throw new InvalidAuctionWindowException("endAt is already in the past");
    }

    Auction auction = new Auction();
    auction.setSellerId(sellerId);
    auction.setTitle(request.title());
    auction.setStartingPrice(request.startingPrice());
    // currentPrice starts AT the starting price, not zero — see reasoning below.
    auction.setCurrentPrice(request.startingPrice());
    auction.setMinIncrement(request.minIncrement());
    auction.setStatus(
            request.startAt().isAfter(now) ? AuctionStatus.SCHEDULED : AuctionStatus.OPEN
    );
    auction.setStartAt(request.startAt());
    auction.setEndAt(request.endAt());

    return toResponse(auctionRepository.saveAndFlush(auction));
}
```

**Edge cases and why each guard exists:**

- **`endAt <= startAt`** — an Auction that closes before it opens can never accept
  a Bid, and `placeBid`'s window check would reject every Bid with a confusing
  `403`. Rejecting at creation gives the Seller an honest error instead.
- **`endAt` in the past** — creates a born-dead Auction. Same reasoning.
- **`currentPrice = startingPrice`, not zero.** `placeBid` reads
  `currentPrice + minIncrement` once `highestBidderId` is set, but reads
  `startingPrice` while it is null. Seeding `currentPrice` keeps the two paths
  consistent and means the close path's `finalPrice = currentPrice` is correct
  even for an Auction that received no Bids.
- **`SCHEDULED` vs `OPEN` at creation** — a future `startAt` must not be
  immediately biddable. `placeBid` already checks both status *and* window, so
  this is belt-and-braces; the status exists so the state is readable without
  recomputing time.

**Deliberately not done:** no scheduled job flips `SCHEDULED → OPEN` at `startAt`.
The window check in `placeBid` already rejects early Bids, so the flip is
cosmetic until something reads status alone. `auction-close-deal` lists Auction
scheduling as out of scope.

### Controller

```java
@PostMapping
@PreAuthorize("hasRole('SELLER')")
public ResponseEntity<AuctionResponse> createAuction(
        @Valid @RequestBody CreateAuctionRequest request,
        @AuthenticationPrincipal Jwt jwt) {

    AuctionResponse response = auctionService.createAuction(jwt.getSubject(), request);
    return ResponseEntity
            .created(URI.create("/api/auctions/" + response.id()))
            .body(response);
}
```

**Trade-off — `hasRole('SELLER')` at the method, not in `SecurityConfig`.**
`SecurityConfig` gates `POST /api/auctions/*/bids` by path, but `POST /api/auctions`
and `POST /api/auctions/{id}/bids` differ only by suffix, and path matchers get
fragile fast. Method security keeps the rule next to the thing it guards.

### Public read

Story 11 ("current highest Bid and price served fast") has no public endpoint —
only `/internal`, which a browser cannot call. Minimum viable:

```java
@GetMapping("/{auctionId}")
public AuctionResponse getAuction(@PathVariable String auctionId) {
    return auctionService.getAuction(auctionId);
}
```

**Trade-off — no Redis yet.** The spec permits a cache but Postgres stays the
source of truth. Adding Redis before there is a measured read problem buys a
second consistency boundary for nothing. Add it when the live feed exists and
is demonstrably slow.

---

## 2. Flyway — now urgent, not optional

**Why now:** `nextAttemptAt` was added as `nullable = false` to an existing table.
Against a non-empty database, `ddl-auto: update` cannot add a NOT NULL column
without a default and the service will fail to start. This is no longer a
tidiness concern.

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate      # Hibernate checks the schema, Flyway owns it
  flyway:
    enabled: true
    baseline-on-migrate: true # existing databases are not empty
```

**Trade-off:** `validate` means a mismatch between entity and schema fails
startup instead of being silently patched. That is the point — a silent patch in
production is how columns drift. Cost: every entity change now needs a migration
file, which is friction you feel immediately and thank later.

**Keep `create-drop` in test config.** Tests run against a throwaway container,
so migrations there only slow the loop down. The trade-off is that a broken
migration is not caught by unit tests — catch it by running the stack.

---

## 3. Missing bid-core tests

Six of seven supporting tests from the spec do not exist. The harness is already
there, so each is a few lines. The one that carries real weight:

```java
@Test
void acceptedBidPublishesExactlyOneBidPlacedEvent() throws Exception {
    mockMvc.perform(post("/api/auctions/{id}/bids", auctionId)
                    .with(bidder(BIDDER_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\": 150.00}"))
            .andExpect(status().isCreated());

    // Drain the queue this service's consumers bind to.
    // One accepted Bid must produce exactly one fact — no more, no less.
    Message first = rabbitTemplate.receive(BID_PLACED_TEST_QUEUE, 5_000);
    assertThat(first).describedAs("bid.placed was published").isNotNull();
    assertThat(rabbitTemplate.receive(BID_PLACED_TEST_QUEUE, 500))
            .describedAs("published exactly once")
            .isNull();
}
```

**Why this one matters most:** every downstream service — agent, notification,
the future live feed — trusts that one accepted Bid means one `bid.placed`.
Nothing currently proves it. A duplicate publish would make every Proxy Bidder
react twice; a missing publish would silently stop the agent-vs-agent flow.

**Edge case worth its own test:** a *rejected* Bid must publish nothing. The
`@TransactionalEventListener(AFTER_COMMIT)` design should guarantee this, but
"should" is what tests are for.

The other five are mechanical — same shape as the existing tests, different
seeded state and expected status:

| Case | Seed | Expect |
|---|---|---|
| Below increment | bid `105` against price `100`, increment `10` | `409` |
| Below starting price | no Bids, bid `50`, start `100` | `422` |
| Closed Auction | status `CLOSED` | `403` |
| Seller bids | JWT subject = `sellerId` | `403` |
| Missing Auction | random UUID | `404` |
| Unauthenticated | no `.with(jwt())` | `401` |

---

## 4. Close and Deal tests

Zero exist, and close carries the exactly-once guarantee that ADR-0004 leans on.

```java
@Test
void repeatedCloseReturnsTheSameDealAndPublishesNothingTwice() throws Exception {
    // first close creates the Deal
    String firstBody = closeAs(SELLER_ID).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    // replaying the close must be inert
    String secondBody = closeAs(SELLER_ID).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

    assertThat(JsonPath.read(secondBody, "$.dealId"))
            .describedAs("same Deal, not a second one")
            .isEqualTo(JsonPath.read(firstBody, "$.dealId"));
    assertThat(dealRepository.findAll()).hasSize(1);
}
```

**Edge cases the close path must survive:**

- **Concurrent close.** Two callers, one `@Version` — one wins, the other gets
  `409`. The unique constraint on `deals.auction_id` is the second net if the
  version check is ever removed.
- **Auction with no Bids.** Closes with `status = CLOSED` and **no** Deal.
  `toCloseAuctionResponse` must return null Deal fields rather than throwing.
- **Close before `endAt`** → `403`, not `409`. It is a rule violation, not a race.

**Open contradiction to resolve first:** the code requires the caller to be the
Seller; `auction-close-deal/spec.md` does not mention caller identity and lists
only one `403` (before `endAt`). Amend the spec or drop the check — do not leave
code and spec disagreeing, or the test encodes the disagreement.

---

## 5. Ticket 08 — agent-vs-agent evidence

The headline demo. Two Proxy Bidders with different Budgets, reacting through
real RabbitMQ until one hits its ceiling.

```java
@Test
void twoProxyBiddersEscalateUntilTheLowerBudgetIsExhausted() {
    // Budgets chosen so the escalation terminates predictably:
    // start 4000, increment 100 → 4100, 4200 … A stops at 4500, B can reach 5000.
    seedProxyBidder("bidder-a", new BigDecimal("4500.00"));
    seedProxyBidder("bidder-b", new BigDecimal("5000.00"));

    publishBidPlaced(new BigDecimal("4000.00"), "human-bidder");

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(auction.getHighestBidderId()).isEqualTo("bidder-b");
        assertThat(auction.getCurrentPrice()).isEqualByComparingTo("4600.00");
    });

    assertThat(reactionRepository.findAll())
            .describedAs("no Reaction exceeded its Budget")
            .allSatisfy(r -> assertThat(r.getProposedAmount())
                    .isLessThanOrEqualTo(budgetOf(r.getProxyBidderId())));
}
```

**Why the Budgets are asymmetric:** equal Budgets produce a tie whose winner
depends on scheduling, which makes the test flaky. Different ceilings give one
deterministic outcome — B outbids A's last valid amount and A cannot answer.

**Why `await()` rather than a fixed sleep:** the chain is event-driven across a
real broker. A sleep either flakes on a slow machine or wastes seconds on a fast
one. Polling an assertion is the honest way to wait for eventual consistency.

**Edge cases this scenario must pin down:**

- **Budget equality is inclusive.** An amount exactly equal to Budget is valid
  (spec story 17). Off-by-one here silently disables the last Bid a Bidder can make.
- **Duplicate delivery.** RabbitMQ redelivers; the reaction uniqueness constraint
  must hold under a genuinely repeated event, not just a repeated method call.
- **Termination.** When neither Proxy Bidder can bid, the flow must stop. A rule
  that never terminates is an infinite bidding war and an infinite test.

**Known risk:** this exercises the retry worker's scheduler on a 2s fixed delay.
If the test is slow or flaky, shorten `proxy-bidder.retry.delay-ms` in test config
rather than adding sleeps.

---

## 6. AI advisor — the load-bearing seam

The whole spec is a build of its own. The part worth getting right first is the
provider abstraction, because everything else hangs off it.

```java
// The entire surface the domain depends on. Provider HTTP shapes,
// auth, and model names live in adapters and never leak past this.
public interface AiAdviceProvider {
    AiAdvice adviseOn(AdviceRequest request);
}

public record AiAdvice(Recommendation recommendation, String reasonCode, String reason) {
    public enum Recommendation { BID, SKIP }
}
```

**Why an interface for what is currently one provider:** the spec's whole
justification is replaceability (stories 17, 19, 40). Groq's free tier, model
availability, and pricing are operational facts that change; the adapter seam is
what stops that churn reaching Proxy Bidder logic. This is the one place where
an abstraction with a single implementation is *not* speculative generality — the
second implementation (Ollama) is named in the spec.

The fallback is the behaviour that matters most:

```java
// agentservice — the orchestrator keeps authority.
private boolean shouldBid(ProxyBidder proxyBidder, AdviceContext context) {
    if (proxyBidder.getMode() == ProxyBidderMode.RULES_ONLY) {
        return true;
    }
    try {
        return adviceClient.adviseOn(context).recommendation() == Recommendation.BID;
    } catch (RuntimeException advisoryFailure) {
        // Timeout, open circuit, 429, 5xx, malformed JSON, unknown enum —
        // every one of them means "we learned nothing", not "do not bid".
        // Falling back to the rules-approved amount is the safe default:
        // the amount was already validated against window, increment and Budget.
        log.info("AI advice unavailable, falling back to rules: reactionId={}", context.reactionId());
        return true;
    }
}
```

**The critical design decision — failure means `BID`, not `SKIP`.** A provider
outage must not silently disable everyone's Proxy Bidder; that would look
identical to the agent being broken. The Bid is already known-valid by the rules
engine, so proceeding is the conservative choice. The inverse (fail to `SKIP`)
would mean an outage quietly costs Bidders auctions they had budgeted for.

**And AI failure must not mark the Reaction failed** (spec story 38). A failed
Reaction means the *Bid* failed. Conflating the two makes the retry worker chase
an advisory problem it cannot fix.

**Config gaps to fix before any of this runs:**

- Resilience4j is in no pom, though the spec requires timeout + circuit breaker.
- The realm has no `aiservice` audience — the `agentservice` client's mapper adds
  only `auctionservice`, so every advice call would `403`.
- `aiservice` uses WebFlux while every other service is servlet MVC. Either switch
  it to `spring-boot-starter-webmvc` + `spring-boot-starter-restclient` for
  consistency, or accept that its security config will not resemble any other
  service's.

**Needs an ADR.** Choosing a provider-neutral adapter with a Groq default
reverses the earlier "Gemini-assisted" decision. Hard to reverse, surprising to a
reader, and the result of a real trade-off — that is exactly the ADR test.

---

## Ordering

1. Auction creation — unblocks manual testing and ticket 08
2. Flyway — before the schema change bites
3. Ticket 08 — the demo worth showing
4. Bid-core tests, starting with exactly-once publish
5. Close/Deal tests, after resolving the seller-authorization contradiction
6. AI advisor — its own build, once the above is proven
