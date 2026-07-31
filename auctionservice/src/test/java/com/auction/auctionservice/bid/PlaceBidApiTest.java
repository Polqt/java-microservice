package com.auction.auctionservice.bid;

import com.auction.auctionservice.TestcontainersConfiguration;
import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.model.Bid;
import com.auction.auctionservice.repository.AuctionRepository;
import com.auction.auctionservice.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavior of the Bid placement API, driven over HTTP against a real Postgres.
 *
 * Auctions are seeded through the repository because Auction creation is out of
 * scope for the bid-core spec — arranging state that way is fine; asserting
 * through it instead of the API would not be.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlaceBidApiTest {

    private static final String SELLER_ID = "seller-1";
    private static final String BIDDER_ID = "bidder-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    private String auctionId;

    @BeforeEach
    void seedOpenAuction() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        auctionId = openAuctionWithMinIncrement(new BigDecimal("10.00"));
    }

    private String openAuctionWithMinIncrement(BigDecimal minIncrement) {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(minIncrement);
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));

        return auctionRepository.saveAndFlush(auction).getId();
    }

    @Test
    void bidderCanPlaceABidOnAnOpenAuctionAndTakeTheLead() throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(bidder(BIDDER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 150.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.auctionId").value(auctionId))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.leading").value(true));

        Auction persisted = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(persisted.getCurrentPrice()).isEqualByComparingTo("150.00");
        assertThat(persisted.getHighestBidderId()).isEqualTo(BIDDER_ID);
    }

    @Test
    void bidBelowTheMinimumIncrementIsBalked() throws Exception {
        placeBid(BIDDER_ID, "150.00").andExpect(status().isCreated());

        // Lead is 150, increment 10, so 155 does not clear the bar.
        placeBid("bidder-2", "155.00")
                .andExpect(status().isConflict());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getHighestBidderId())
                .describedAs("a balked bid must not take the lead")
                .isEqualTo(BIDDER_ID);
    }

    @Test
    void bidBelowTheStartingPriceIsRejectedAsUnprocessable() throws Exception {
        // No bids yet, so the floor is the starting price of 100 — not the increment.
        placeBid(BIDDER_ID, "50.00")
                .andExpect(status().isUnprocessableContent());

        assertThat(bidRepository.findAll()).isEmpty();
    }

    @Test
    void bidOnAClosedAuctionIsForbidden() throws Exception {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();
        auction.setStatus(AuctionStatus.CLOSED);
        auctionRepository.saveAndFlush(auction);

        placeBid(BIDDER_ID, "150.00")
                .andExpect(status().isForbidden());

        assertThat(bidRepository.findAll()).isEmpty();
    }

    @Test
    void sellerCannotBidOnTheirOwnAuction() throws Exception {
        // Shill bidding: the Seller inflating their own Auction.
        placeBid(SELLER_ID, "150.00")
                .andExpect(status().isForbidden());

        assertThat(bidRepository.findAll()).isEmpty();
    }

    @Test
    void bidOnAnUnknownAuctionIsNotFound() throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/bids", UUID.randomUUID().toString())
                        .with(bidder(BIDDER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 150.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedBidIsRejected() throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 150.00}"))
                .andExpect(status().isUnauthorized());

        assertThat(bidRepository.findAll()).isEmpty();
    }

    /**
     * The reason this spec exists: two Bidders bid the same amount at the same moment,
     * and exactly one may take the lead.
     *
     * The auction is seeded with a zero minimum increment on purpose. With a non-zero
     * increment, a second bid that arrived after the price had already risen would be
     * rejected as too low — so the test would pass without the version guard ever
     * being exercised. At zero increment, both bids are individually valid against the
     * state each one reads, and optimistic locking is the only thing that can
     * arbitrate between them.
     */
    @Test
    void twoSimultaneousBidsProduceExactlyOneWinner() throws Exception {
        auctionId = openAuctionWithMinIncrement(BigDecimal.ZERO);
        String bidBody = "{\"amount\": 150.00}";
        CountDownLatch releaseBothBids = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Integer>> racingBids = List.of(
                    bidOnRelease("bidder-a", bidBody, releaseBothBids),
                    bidOnRelease("bidder-b", bidBody, releaseBothBids)
            );

            List<Future<Integer>> submitted = racingBids.stream()
                    .map(threads::submit)
                    .toList();

            releaseBothBids.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : submitted) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses)
                    .describedAs("one bid accepted, one balked")
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            threads.shutdownNow();
        }

        Auction persisted = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(persisted.getCurrentPrice()).isEqualByComparingTo("150.00");
        assertThat(persisted.getHighestBidderId()).isIn("bidder-a", "bidder-b");

        List<Bid> recorded = bidRepository.findAll();
        assertThat(recorded)
                .describedAs("the balked bid must leave no trace")
                .hasSize(1);
        assertThat(recorded.getFirst().getBidderId())
                .isEqualTo(persisted.getHighestBidderId());
    }

    private Callable<Integer> bidOnRelease(String bidderId, String body, CountDownLatch release) {
        return () -> {
            release.await();
            return mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                            .with(bidder(bidderId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private ResultActions placeBid(String bidderId, String amount) throws Exception {
        return mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                .with(bidder(bidderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }

    /** A Bidder identity as the resource server would see it: subject + BIDDER realm role. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor bidder(String bidderId) {
        return jwt()
                .jwt(token -> token.subject(bidderId))
                .authorities(new SimpleGrantedAuthority("ROLE_BIDDER"));
    }
}
