package com.auction.auctionservice.close;

import com.auction.auctionservice.TestcontainersConfiguration;
import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.model.Bid;
import com.auction.auctionservice.repository.AuctionRepository;
import com.auction.auctionservice.repository.BidRepository;
import com.auction.auctionservice.repository.DealRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavior of Auction Close and Deal creation.
 *
 * Close carries the exactly-once guarantee ADR-0004 depends on: the platform
 * handles no money, so the Deal is the only record pairing winner with Seller.
 * Creating two, or none, breaks the handoff the whole product exists for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CloseAuctionApiTest {

    private static final String SELLER_ID = "seller-1";
    private static final String WINNER_ID = "bidder-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private DealRepository dealRepository;

    private String auctionId;

    @BeforeEach
    void resetState() {
        dealRepository.deleteAll();
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
    }

    /** An Auction already past its end time, so Close is permitted. */
    private String endedAuction() {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartAt(LocalDateTime.now().minusHours(2));
        auction.setEndAt(LocalDateTime.now().minusMinutes(1));
        return auctionRepository.saveAndFlush(auction).getId();
    }

    private void seedWinningBid(String bidderId, String amount) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        auction.setCurrentPrice(new BigDecimal(amount));
        auction.setHighestBidderId(bidderId);
        auctionRepository.saveAndFlush(auction);

        Bid bid = new Bid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(new BigDecimal(amount));
        bidRepository.saveAndFlush(bid);
    }

    @Test
    void closingAnEndedAuctionWithBidsCreatesExactlyOneDeal() throws Exception {
        auctionId = endedAuction();
        seedWinningBid(WINNER_ID, "150.00");

        closeAs(SELLER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.winningBidderId").value(WINNER_ID))
                .andExpect(jsonPath("$.finalPrice").value(150.00))
                .andExpect(jsonPath("$.dealId").isNotEmpty());

        assertThat(dealRepository.findAll()).hasSize(1);
        assertThat(auctionRepository.findById(auctionId).orElseThrow().getClosedAt())
                .describedAs("closedAt is stamped so the outcome has a time")
                .isNotNull();
    }

    /**
     * RabbitMQ redelivers and clients retry, so Close must be replay-safe:
     * the same Deal comes back rather than a second one being minted.
     */
    @Test
    void repeatedCloseReturnsTheSameDeal() throws Exception {
        auctionId = endedAuction();
        seedWinningBid(WINNER_ID, "150.00");

        String firstBody = closeAs(SELLER_ID).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String firstDealId = JsonPath.read(firstBody, "$.dealId");

        // Comparing whole bodies would over-specify: closedAt is returned with
        // nanosecond precision on the first close and microsecond precision on
        // the replay, because Postgres truncates on the round trip.
        closeAs(SELLER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.winningBidderId").value(WINNER_ID))
                .andExpect(jsonPath("$.finalPrice").value(150.00))
                .andExpect(jsonPath("$.dealId").value(firstDealId));

        assertThat(dealRepository.findAll())
                .describedAs("exactly one Deal, however many times Close is called")
                .hasSize(1);
    }

    /** No Bids means no winner — the Auction still closes, but nobody is paired. */
    @Test
    void closingAnAuctionWithoutBidsCreatesNoDeal() throws Exception {
        auctionId = endedAuction();

        closeAs(SELLER_ID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.dealId").doesNotExist())
                .andExpect(jsonPath("$.winningBidderId").doesNotExist());

        assertThat(dealRepository.findAll()).isEmpty();
    }

    @Test
    void closingBeforeTheEndTimeIsForbidden() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Still running");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));
        auctionId = auctionRepository.saveAndFlush(auction).getId();

        // A rule violation, not a race — 403, never 409.
        closeAs(SELLER_ID).andExpect(status().isForbidden());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.OPEN);
    }

    /**
     * Not-found, not forbidden: a foreign caller must not even learn the Auction
     * exists. Same rule DealNotFoundException and edit/cancel already state — this
     * used to be the one holdout returning 403, fixed to match.
     */
    @Test
    void onlyTheOwningSellerMayClose() throws Exception {
        auctionId = endedAuction();
        seedWinningBid(WINNER_ID, "150.00");

        closeAs("someone-else").andExpect(status().isNotFound());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .describedAs("a foreign caller must not close the Auction")
                .isEqualTo(AuctionStatus.OPEN);
        assertThat(dealRepository.findAll()).isEmpty();
    }

    @Test
    void closingAnUnknownAuctionIsNotFound() throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/close", UUID.randomUUID().toString())
                        .with(seller(SELLER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedCloseIsRejected() throws Exception {
        auctionId = endedAuction();

        mockMvc.perform(post("/api/auctions/{auctionId}/close", auctionId))
                .andExpect(status().isUnauthorized());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.OPEN);
    }

    private ResultActions closeAs(String callerId) throws Exception {
        return mockMvc.perform(post("/api/auctions/{auctionId}/close", auctionId)
                .with(seller(callerId)));
    }

    private static RequestPostProcessor seller(String sellerId) {
        return jwt()
                .jwt(token -> token.subject(sellerId))
                .authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
