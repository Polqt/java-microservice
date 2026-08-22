package com.auction.auctionservice.edit;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ticket 05 — Seller constrained edit. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UpdateAuctionApiTest {

    private static final String SELLER_ID = "seller-1";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @BeforeEach
    void resetState() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
    }

    @Test
    void titleEditSucceedsEvenWithBidsPresent() throws Exception {
        String auctionId = seedOpenAuction();
        seedBid(auctionId, "bidder-1", "150.00");

        patchAs(SELLER_ID, auctionId, "{\"title\": \"New title\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"));
    }

    @Test
    void startingPriceEditIsRejectedOnceABidExists() throws Exception {
        String auctionId = seedOpenAuction();
        seedBid(auctionId, "bidder-1", "150.00");

        patchAs(SELLER_ID, auctionId, "{\"startingPrice\": 200.00}")
                .andExpect(status().isUnprocessableContent());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStartingPrice())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void startingPriceEditSucceedsWithNoBids() throws Exception {
        String auctionId = seedOpenAuction();

        patchAs(SELLER_ID, auctionId, "{\"startingPrice\": 200.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startingPrice").value(200.00));
    }

    @Test
    void endAtExtensionSucceeds() throws Exception {
        String auctionId = seedOpenAuction();
        String extended = LocalDateTime.now().plusHours(5).format(ISO);

        patchAs(SELLER_ID, auctionId, "{\"endAt\": \"" + extended + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void endAtShorteningIsRejected() throws Exception {
        String auctionId = seedOpenAuction();
        String shortened = LocalDateTime.now().minusMinutes(30).format(ISO);

        patchAs(SELLER_ID, auctionId, "{\"endAt\": \"" + shortened + "\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void editOnAClosedAuctionIsRejected() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.CLOSED);
        auction.setStartAt(LocalDateTime.now().minusHours(2));
        auction.setEndAt(LocalDateTime.now().minusHours(1));
        String auctionId = auctionRepository.saveAndFlush(auction).getId();

        patchAs(SELLER_ID, auctionId, "{\"title\": \"New title\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void editOnACancelledAuctionIsRejected() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));
        String auctionId = auctionRepository.saveAndFlush(auction).getId();

        patchAs(SELLER_ID, auctionId, "{\"title\": \"New title\"}")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void foreignSellerEditIsNotFoundAndAuctionUnchanged() throws Exception {
        String auctionId = seedOpenAuction();

        patchAs("someone-else", auctionId, "{\"title\": \"Hijacked\"}")
                .andExpect(status().isNotFound());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getTitle())
                .isEqualTo("Vintage film camera");
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        String auctionId = seedOpenAuction();

        mockMvc.perform(patch("/api/auctions/{auctionId}", auctionId)
                        .with(jwt().jwt(t -> t.subject(SELLER_ID)).authorities(new SimpleGrantedAuthority("ROLE_BIDDER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"New title\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedEditIsUnauthorized() throws Exception {
        String auctionId = seedOpenAuction();

        mockMvc.perform(patch("/api/auctions/{auctionId}", auctionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"New title\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentEditsOneSucceedsOneConflicts() throws Exception {
        String auctionId = seedOpenAuction();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Integer>> racingEdits = List.of(
                    editOnRelease(auctionId, "{\"title\": \"Edit A\"}", release),
                    editOnRelease(auctionId, "{\"title\": \"Edit B\"}", release)
            );

            List<Future<Integer>> submitted = racingEdits.stream().map(threads::submit).toList();
            release.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : submitted) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            threads.shutdownNow();
        }
    }

    private Callable<Integer> editOnRelease(String auctionId, String body, CountDownLatch release) {
        return () -> {
            release.await();
            return patchAs(SELLER_ID, auctionId, body).andReturn().getResponse().getStatus();
        };
    }

    private String seedOpenAuction() {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));
        return auctionRepository.saveAndFlush(auction).getId();
    }

    private void seedBid(String auctionId, String bidderId, String amount) {
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

    private ResultActions patchAs(String sellerId, String auctionId, String body) throws Exception {
        return mockMvc.perform(patch("/api/auctions/{auctionId}", auctionId)
                .with(seller(sellerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static RequestPostProcessor seller(String sellerId) {
        return jwt().jwt(token -> token.subject(sellerId)).authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
