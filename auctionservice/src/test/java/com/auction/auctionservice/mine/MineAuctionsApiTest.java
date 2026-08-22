package com.auction.auctionservice.mine;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ticket 04 — "mine" Auctions for Sellers and Bidders. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MineAuctionsApiTest {

    private static final String SELLER_ID = "seller-1";
    private static final String OTHER_SELLER_ID = "seller-2";
    private static final String BIDDER_ID = "bidder-1";

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
    void sellersOwnAuctionsIncludeEveryStatus() throws Exception {
        seedAuction(SELLER_ID, "Scheduled item", AuctionStatus.SCHEDULED,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        seedAuction(SELLER_ID, "Open item", AuctionStatus.OPEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        seedAuction(SELLER_ID, "Closed item", AuctionStatus.CLOSED,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        seedAuction(OTHER_SELLER_ID, "Someone else's item", AuctionStatus.OPEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        mockMvc.perform(get("/api/auctions/mine").with(seller(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void wrongRoleOnSellerMineIsForbidden() throws Exception {
        mockMvc.perform(get("/api/auctions/mine").with(bidder(BIDDER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedSellerMineIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auctions/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bidderMineIndicatesCurrentLead() throws Exception {
        String leadingAuctionId = seedAuction(SELLER_ID, "Leading here", AuctionStatus.OPEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        seedBid(leadingAuctionId, BIDDER_ID, "150.00", true);

        String outbidAuctionId = seedAuction(SELLER_ID, "Outbid here", AuctionStatus.OPEN,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        seedBid(outbidAuctionId, BIDDER_ID, "110.00", false);
        seedBid(outbidAuctionId, "bidder-2", "120.00", true);

        mockMvc.perform(get("/api/bids/mine").with(bidder(BIDDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.auctionId == '" + leadingAuctionId + "')].leading").value(true))
                .andExpect(jsonPath("$.content[?(@.auctionId == '" + outbidAuctionId + "')].leading").value(false));
    }

    @Test
    void wrongRoleOnBidderMineIsForbidden() throws Exception {
        mockMvc.perform(get("/api/bids/mine").with(seller(SELLER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedBidderMineIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/bids/mine"))
                .andExpect(status().isUnauthorized());
    }

    private String seedAuction(String sellerId, String title, AuctionStatus status,
                                LocalDateTime startAt, LocalDateTime endAt) {
        Auction auction = new Auction();
        auction.setSellerId(sellerId);
        auction.setTitle(title);
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(status);
        auction.setStartAt(startAt);
        auction.setEndAt(endAt);
        return auctionRepository.saveAndFlush(auction).getId();
    }

    private void seedBid(String auctionId, String bidderId, String amount, boolean leading) {
        if (leading) {
            Auction auction = auctionRepository.findById(auctionId).orElseThrow();
            auction.setCurrentPrice(new BigDecimal(amount));
            auction.setHighestBidderId(bidderId);
            auctionRepository.saveAndFlush(auction);
        }

        Bid bid = new Bid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(new BigDecimal(amount));
        bidRepository.saveAndFlush(bid);
    }

    private static RequestPostProcessor seller(String sellerId) {
        return jwt().jwt(token -> token.subject(sellerId)).authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
    }

    private static RequestPostProcessor bidder(String bidderId) {
        return jwt().jwt(token -> token.subject(bidderId)).authorities(new SimpleGrantedAuthority("ROLE_BIDDER"));
    }
}
