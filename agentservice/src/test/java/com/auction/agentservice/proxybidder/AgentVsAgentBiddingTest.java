package com.auction.agentservice.proxybidder;

import com.auction.agentservice.TestcontainersConfiguration;
import com.auction.agentservice.auction.AgentBidCommand;
import com.auction.agentservice.auction.AgentBidResponse;
import com.auction.agentservice.auction.AuctionBidCommandClient;
import com.auction.agentservice.auction.AuctionBidStateClient;
import com.auction.agentservice.auction.AuctionBidStateResponse;
import com.auction.agentservice.auction.AuctionStatus;
import com.auction.agentservice.config.RabbitConfig;
import com.auction.agentservice.event.BidPlacedEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 08 — the standout evidence: two Proxy Bidders competing on one Auction,
 * driven entirely through real RabbitMQ delivery, until one is priced out by its
 * own Budget.
 *
 * The Auction clients are mocked, same seam as {@link ProxyBidderReactionServiceTest}
 * — auctionservice's own concurrency is already exhaustively proven by its own
 * {@code PlaceBidApiTest}. What is real here is agentservice's own persistence
 * (Postgres) and the message bus (RabbitMQ): the mocked command client, on success,
 * publishes the same {@code bid.placed} fact the real auctionservice would after an
 * AFTER_COMMIT publish, so the real {@code BidPlacedAgentListener} picks it up and
 * drives the next round — the actual mechanism under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgentVsAgentBiddingTest {

    private static final String AUCTION_ID = "auction-1";
    private static final String BIDDER_A = "bidder-a";
    private static final String BIDDER_B = "bidder-b";
    private static final BigDecimal MIN_INCREMENT = new BigDecimal("100.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProxyBidderRepository proxyBidderRepository;

    @Autowired
    private ProxyBidderReactionRepository reactionRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private AuctionBidStateClient stateClient;

    @MockitoBean
    private AuctionBidCommandClient commandClient;

    @BeforeEach
    void resetState() {
        reactionRepository.deleteAll();
        proxyBidderRepository.deleteAll();
    }

    /**
     * The headline scenario. B's Budget (5000) is strictly higher than A's (4500),
     * so B must always be able to counter any price A can reach, and only stops
     * escalating once A is priced out — this holds regardless of which of A or B
     * the repository returns first on a given pass (traced by hand for both orders;
     * the settlement price differs slightly by order — 4500 or 4600 — but the final
     * leader does not), so the assertions below only pin what is actually invariant.
     *
     * Both Proxy Bidders are created through the real authenticated
     * {@code POST /api/proxy-bidders} endpoint (ticket 08's own opening criterion:
     * "Two authenticated Bidders can configure active Proxy Bidders") rather than
     * seeded directly — the repository-seeding shortcut used by the other tests in
     * this class would leave that criterion unproven.
     */
    @Test
    void twoProxyBiddersEscalateThroughRealRabbitMqUntilTheLowerBudgetIsPricedOut() throws Exception {
        FakeAuctionServer fake = new FakeAuctionServer(
                AUCTION_ID, new BigDecimal("4000.00"), "human-bidder", MIN_INCREMENT, rabbitTemplate);
        given(stateClient.getBidState(AUCTION_ID)).willAnswer(invocation -> fake.currentState());
        given(commandClient.placeBid(anyString(), any()))
                .willAnswer(invocation -> fake.placeBid(invocation.getArgument(1)));

        String bidderAId = createProxyBidder(BIDDER_A, "4500.00");
        String bidderBId = createProxyBidder(BIDDER_B, "5000.00");
        ProxyBidder bidderA = proxyBidderRepository.findById(bidderAId).orElseThrow();
        ProxyBidder bidderB = proxyBidderRepository.findById(bidderBId).orElseThrow();

        UUID initialEventId = UUID.randomUUID();
        publishHumanBid(initialEventId, new BigDecimal("4000.00"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            FakeAuctionServer.State settled = fake.snapshot();
            assertThat(settled.highestBidderId())
                    .describedAs("B's higher Budget must win the escalation")
                    .isEqualTo(BIDDER_B);
            assertThat(settled.currentPrice())
                    .describedAs("cascade halts once A can no longer afford the next increment")
                    .isIn(new BigDecimal("4500.00"), new BigDecimal("4600.00"));
            // Not just "looks settled" — every reaction must have reached a terminal
            // outcome, or the retry worker could still act on a PENDING one later and
            // reopen the cascade after this point, making reactionCountBeforeRedelivery
            // below an unstable snapshot rather than the true settled state.
            assertThat(reactionRepository.findAllByOutcome(ProxyBidderReactionOutcome.PENDING))
                    .describedAs("nothing left for the retry worker to act on")
                    .isEmpty();
        });

        List<ProxyBidderReaction> allReactions = reactionRepository.findAll();

        assertThat(allReactions)
                .describedAs("no amount actually sent to the Auction ever exceeded its Proxy Bidder's Budget — "
                        + "a SKIPPED reaction may still record a considered-and-rejected amount above Budget, "
                        + "that is the guard working, not a violation of it")
                .filteredOn(reaction -> reaction.getAttemptCount() > 0)
                .allSatisfy(reaction -> {
                    BigDecimal budget = reaction.getProxyBidderId().equals(bidderA.getId())
                            ? bidderA.getBudget()
                            : bidderB.getBudget();
                    assertThat(reaction.getProposedAmount()).isLessThanOrEqualTo(budget);
                });

        assertThat(allReactions)
                .describedAs("A must eventually be priced out once it can no longer afford the next increment")
                .anyMatch(r -> r.getProxyBidderId().equals(bidderA.getId())
                        && r.getOutcome() == ProxyBidderReactionOutcome.SKIPPED);
    }

    /**
     * Duplicate delivery, isolated from the cascade above. With two Proxy Bidders,
     * every successful bid publishes another event with a fresh random eventId, and
     * some of those keep arriving — each settling into one harmless terminal SKIPPED
     * row — even after price/lead look stable, which makes an exact reaction-count
     * comparison across that window inherently unstable. With one Proxy Bidder that
     * already leads after its own single successful bid, no further event is ever
     * published, so the reaction count is genuinely fixed at exactly 2 (its own
     * success, and the SKIPPED reaction from noticing it already leads) — the
     * redelivered fact has nothing left to do but be blocked.
     */
    @Test
    void duplicateDeliveryOfAnAlreadyProcessedFactIsANoOp() {
        proxyBidderRepository.saveAndFlush(
                new ProxyBidder(AUCTION_ID, BIDDER_A, new BigDecimal("4500.00")));

        FakeAuctionServer fake = new FakeAuctionServer(
                AUCTION_ID, new BigDecimal("4000.00"), "human-bidder", MIN_INCREMENT, rabbitTemplate);
        given(stateClient.getBidState(AUCTION_ID)).willAnswer(invocation -> fake.currentState());
        given(commandClient.placeBid(anyString(), any()))
                .willAnswer(invocation -> fake.placeBid(invocation.getArgument(1)));

        UUID eventId = UUID.randomUUID();
        publishHumanBid(eventId, new BigDecimal("4000.00"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(fake.snapshot().highestBidderId()).isEqualTo(BIDDER_A);
            assertThat(reactionRepository.findAll()).hasSize(2);
        });

        // RabbitMQ redelivers on failure/requeue — the normal case, not an edge case.
        publishHumanBid(eventId, new BigDecimal("4000.00"));

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(reactionRepository.findAll()).hasSize(2)
        );

        assertThat(fake.snapshot().currentPrice()).isEqualByComparingTo("4100.00");
        verify(commandClient, times(1)).placeBid(anyString(), any());
    }

    /**
     * Isolates the exact-Budget boundary from the emergent cascade above, where the
     * settlement price (and therefore which Proxy Bidder happens to hit its Budget
     * exactly) depends on row order. Here the state is constructed directly so the
     * only valid proposal is exactly the Budget.
     */
    @Test
    void aProposalExactlyAtBudgetSucceeds() {
        proxyBidderRepository.saveAndFlush(
                new ProxyBidder(AUCTION_ID, BIDDER_A, new BigDecimal("4500.00")));

        FakeAuctionServer fake = new FakeAuctionServer(
                AUCTION_ID, new BigDecimal("4400.00"), "human-bidder", MIN_INCREMENT, rabbitTemplate);
        given(stateClient.getBidState(AUCTION_ID)).willAnswer(invocation -> fake.currentState());
        given(commandClient.placeBid(anyString(), any()))
                .willAnswer(invocation -> fake.placeBid(invocation.getArgument(1)));

        publishHumanBid(UUID.randomUUID(), new BigDecimal("4400.00"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<ProxyBidderReaction> succeeded =
                    reactionRepository.findAllByOutcome(ProxyBidderReactionOutcome.SUCCEEDED);
            assertThat(succeeded).hasSize(1);
            assertThat(succeeded.getFirst().getProposedAmount()).isEqualByComparingTo("4500.00");
        });

        assertThat(fake.snapshot().highestBidderId()).isEqualTo(BIDDER_A);
        assertThat(fake.snapshot().currentPrice()).isEqualByComparingTo("4500.00");
    }

    /**
     * "Concurrent reactions resolve through Auction optimistic locking; one command
     * leads and stale competitors balk" — a lost race at auctionservice surfaces to
     * agentservice as 409 Conflict. It must be terminal (BALKED), not retried.
     */
    @Test
    void aLostOptimisticLockRaceBalksWithoutRetrying() {
        ProxyBidder proxyBidder = proxyBidderRepository.saveAndFlush(
                new ProxyBidder(AUCTION_ID, BIDDER_A, new BigDecimal("4500.00")));

        given(stateClient.getBidState(AUCTION_ID)).willReturn(new AuctionBidStateResponse(
                AUCTION_ID, new BigDecimal("1000.00"), new BigDecimal("4000.00"), MIN_INCREMENT,
                AuctionStatus.OPEN, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1),
                "human-bidder"
        ));
        given(commandClient.placeBid(anyString(), any())).willThrow(
                HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null));

        publishHumanBid(UUID.randomUUID(), new BigDecimal("4000.00"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<ProxyBidderReaction> balked =
                    reactionRepository.findAllByOutcome(ProxyBidderReactionOutcome.BALKED);
            assertThat(balked).hasSize(1);
            assertThat(balked.getFirst().getProxyBidderId()).isEqualTo(proxyBidder.getId());
        });

        verify(commandClient, times(1)).placeBid(anyString(), any());
    }

    /** Real, authenticated Proxy Bidder creation — see the Javadoc on the main scenario test. */
    private String createProxyBidder(String bidderId, String budget) throws Exception {
        String body = mockMvc.perform(post("/api/proxy-bidders")
                        .with(bidder(bidderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auctionId\": \"" + AUCTION_ID + "\", \"budget\": " + budget + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static RequestPostProcessor bidder(String bidderId) {
        return jwt()
                .jwt(token -> token.subject(bidderId))
                .authorities(new SimpleGrantedAuthority("ROLE_BIDDER"));
    }

    private void publishHumanBid(UUID eventId, BigDecimal amount) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.AUCTION_EVENTS_EXCHANGE,
                RabbitConfig.BID_PLACED_KEY,
                new BidPlacedEvent(
                        eventId, AUCTION_ID, UUID.randomUUID().toString(),
                        "human-bidder", amount, LocalDateTime.now()
                )
        );
    }

    /**
     * A minimal, in-memory stand-in for auctionservice: tracks current price and
     * lead, enforces the same optimistic-lock semantics via compare-and-set, and —
     * critically — publishes the same {@code bid.placed} fact onto the real exchange
     * that the real service would after its own AFTER_COMMIT publish, so the other
     * Proxy Bidder's real listener reacts to it.
     */
    private static final class FakeAuctionServer {

        record State(BigDecimal currentPrice, String highestBidderId) {
        }

        private final String auctionId;
        private final BigDecimal minIncrement;
        private final RabbitTemplate rabbitTemplate;
        private final AtomicReference<State> state;

        FakeAuctionServer(
                String auctionId,
                BigDecimal startingPrice,
                String startingLeader,
                BigDecimal minIncrement,
                RabbitTemplate rabbitTemplate
        ) {
            this.auctionId = auctionId;
            this.minIncrement = minIncrement;
            this.rabbitTemplate = rabbitTemplate;
            this.state = new AtomicReference<>(new State(startingPrice, startingLeader));
        }

        State snapshot() {
            return state.get();
        }

        AuctionBidStateResponse currentState() {
            State current = state.get();
            return new AuctionBidStateResponse(
                    auctionId, current.currentPrice(), current.currentPrice(), minIncrement,
                    AuctionStatus.OPEN,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1),
                    current.highestBidderId()
            );
        }

        AgentBidResponse placeBid(AgentBidCommand command) {
            State current = state.get();
            BigDecimal requiredMinimum = current.currentPrice().add(minIncrement);
            boolean accepted = command.amount().compareTo(requiredMinimum) >= 0
                    && state.compareAndSet(current, new State(command.amount(), command.bidderId()));

            if (!accepted) {
                throw HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null);
            }

            rabbitTemplate.convertAndSend(
                    RabbitConfig.AUCTION_EVENTS_EXCHANGE,
                    RabbitConfig.BID_PLACED_KEY,
                    new BidPlacedEvent(
                            UUID.randomUUID(), auctionId, UUID.randomUUID().toString(),
                            command.bidderId(), command.amount(), LocalDateTime.now()
                    )
            );

            return new AgentBidResponse(
                    UUID.randomUUID().toString(), auctionId, command.amount(), command.amount(),
                    true, LocalDateTime.now()
            );
        }
    }
}
