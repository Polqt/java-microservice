package com.auction.auctionservice.cancel;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ticket 06 — Seller cancel. No hard delete anywhere. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CancelAuctionApiTest {

    private static final String SELLER_ID = "seller-1";

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
    void cancelWithNoBidsSucceeds() throws Exception {
        String auctionId = seedOpenAuction();

        cancelAs(SELLER_ID, auctionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.CANCELLED);
    }

    @Test
    void cancelWithBidsPresentIsRejected() throws Exception {
        String auctionId = seedOpenAuction();
        seedBid(auctionId, "bidder-1", "150.00");

        cancelAs(SELLER_ID, auctionId).andExpect(status().isUnprocessableContent());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.OPEN);
    }

    @Test
    void aCancelledAuctionRejectsASubsequentBid() throws Exception {
        String auctionId = seedOpenAuction();
        cancelAs(SELLER_ID, auctionId).andExpect(status().isOk());

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(jwt().jwt(t -> t.subject("bidder-1")).authorities(new SimpleGrantedAuthority("ROLE_BIDDER")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 150.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCancelledAuctionIsStillReadable() throws Exception {
        String auctionId = seedOpenAuction();
        cancelAs(SELLER_ID, auctionId).andExpect(status().isOk());

        mockMvc.perform(get("/api/auctions/{auctionId}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void foreignSellerCancelIsNotFound() throws Exception {
        String auctionId = seedOpenAuction();

        cancelAs("someone-else", auctionId).andExpect(status().isNotFound());

        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus())
                .isEqualTo(AuctionStatus.OPEN);
    }

    @Test
    void cancelOnAnEndedAuctionIsRejected() throws Exception {
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

        cancelAs(SELLER_ID, auctionId).andExpect(status().isUnprocessableContent());
    }

    @Test
    void cancelOnAnUnknownAuctionIsNotFound() throws Exception {
        cancelAs(SELLER_ID, UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedCancelIsUnauthorized() throws Exception {
        String auctionId = seedOpenAuction();

        mockMvc.perform(post("/api/auctions/{auctionId}/cancel", auctionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIsForbidden() throws Exception {
        String auctionId = seedOpenAuction();

        mockMvc.perform(post("/api/auctions/{auctionId}/cancel", auctionId)
                        .with(jwt().jwt(t -> t.subject(SELLER_ID)).authorities(new SimpleGrantedAuthority("ROLE_BIDDER"))))
                .andExpect(status().isForbidden());
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

    private ResultActions cancelAs(String sellerId, String auctionId) throws Exception {
        return mockMvc.perform(post("/api/auctions/{auctionId}/cancel", auctionId)
                .with(seller(sellerId)));
    }

    private static RequestPostProcessor seller(String sellerId) {
        return jwt().jwt(token -> token.subject(sellerId)).authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
