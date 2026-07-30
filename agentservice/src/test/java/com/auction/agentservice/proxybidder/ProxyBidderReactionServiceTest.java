package com.auction.agentservice.proxybidder;

import com.auction.agentservice.TestcontainersConfiguration;
import com.auction.agentservice.auction.AuctionBidCommandClient;
import com.auction.agentservice.auction.AuctionBidStateClient;
import com.auction.agentservice.auction.AuctionBidStateResponse;
import com.auction.agentservice.auction.AuctionStatus;
import com.auction.agentservice.event.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Behavior of Proxy Bidder reactions against a real Postgres.
 *
 * The two Auction clients are mocked: they are network collaborators owned by
 * another service, so stubbing them keeps the seam at this service's boundary.
 * Everything else — persistence, uniqueness, backoff — is exercised for real.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProxyBidderReactionServiceTest {

    private static final String AUCTION_ID = "auction-1";
    private static final String BIDDER_ID = "bidder-1";

    @Autowired
    private ProxyBidderReactionService reactionService;

    @Autowired
    private ProxyBidderRepository proxyBidderRepository;

    @Autowired
    private ProxyBidderReactionRepository reactionRepository;

    @MockitoBean
    private AuctionBidStateClient stateClient;

    @MockitoBean
    private AuctionBidCommandClient commandClient;

    @BeforeEach
    void seedActiveProxyBidder() {
        reactionRepository.deleteAll();
        proxyBidderRepository.deleteAll();

        proxyBidderRepository.saveAndFlush(
                new ProxyBidder(AUCTION_ID, BIDDER_ID, new BigDecimal("5000.00"))
        );

        given(stateClient.getBidState(anyString())).willReturn(openAuctionAt("4000.00"));
    }

    private AuctionBidStateResponse openAuctionAt(String currentPrice) {
        return new AuctionBidStateResponse(
                AUCTION_ID,
                new BigDecimal("1000.00"),
                new BigDecimal(currentPrice),
                new BigDecimal("100.00"),
                AuctionStatus.OPEN,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1),
                "someone-else"
        );
    }

    /**
     * Ticket 06: "Duplicate `bid.placed` delivery cannot create a second Auction
     * command for the same reaction." RabbitMQ redelivers, so this is the normal
     * case rather than an edge case.
     */
    @Test
    void redeliveredBidPlacedEventDoesNotSendASecondCommand() {
        BidPlacedEvent event = bidPlaced(UUID.randomUUID());

        reactionService.reactTo(event);
        reactionService.reactTo(event);

        verify(commandClient, times(1)).placeBid(anyString(), any());
        assertThat(reactionRepository.findAll())
                .describedAs("one reaction per (proxyBidder, sourceEvent) pair")
                .hasSize(1);
    }

    /** Ticket 05: proposed amount is current price plus minimum increment. */
    @Test
    void reactionBidsOneIncrementAboveTheCurrentPrice() {
        reactionService.reactTo(bidPlaced(UUID.randomUUID()));

        ProxyBidderReaction reaction = reactionRepository.findAll().getFirst();
        assertThat(reaction.getProposedAmount()).isEqualByComparingTo("4100.00");
        assertThat(reaction.getOutcome()).isEqualTo(ProxyBidderReactionOutcome.SUCCEEDED);
    }

    /**
     * Ticket 06: "Network failures and `5xx` responses retry only up to the
     * configured bound with backoff." A transient failure must stay PENDING and
     * must not be eligible again immediately.
     */
    @Test
    void transientFailureStaysPendingAndBacksOff() {
        willThrow(HttpServerErrorException.create(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "bad gateway", null, null, null))
                .given(commandClient).placeBid(anyString(), any());

        LocalDateTime beforeReacting = LocalDateTime.now();
        reactionService.reactTo(bidPlaced(UUID.randomUUID()));

        ProxyBidderReaction reaction = reactionRepository.findAll().getFirst();
        assertThat(reaction.getOutcome())
                .describedAs("transient failure is retryable, not terminal")
                .isEqualTo(ProxyBidderReactionOutcome.PENDING);
        assertThat(reaction.getAttemptCount()).isEqualTo(1);
        assertThat(reaction.getNextAttemptAt())
                .describedAs("backed off, so the next sweep skips it")
                .isAfter(beforeReacting);
    }

    /**
     * Ticket 05/06: "No command is sent when the proposed amount exceeds Budget",
     * and Budget is a hard ceiling (spec story 17).
     */
    @Test
    void noCommandIsSentWhenTheNextIncrementWouldExceedBudget() {
        given(stateClient.getBidState(anyString())).willReturn(openAuctionAt("5000.00"));

        reactionService.reactTo(bidPlaced(UUID.randomUUID()));

        verify(commandClient, never()).placeBid(anyString(), any());
        assertThat(reactionRepository.findAll().getFirst().getOutcome())
                .isEqualTo(ProxyBidderReactionOutcome.SKIPPED);
    }

    private BidPlacedEvent bidPlaced(UUID eventId) {
        return new BidPlacedEvent(
                eventId,
                AUCTION_ID,
                "bid-1",
                "someone-else",
                new BigDecimal("4000.00"),
                LocalDateTime.now()
        );
    }
}
