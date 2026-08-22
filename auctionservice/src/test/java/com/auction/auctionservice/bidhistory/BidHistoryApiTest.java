package com.auction.auctionservice.bidhistory;

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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public Bid history — ticket 02. Unauthenticated, and never exposes anything
 * beyond bidder identifier, amount, and timestamp (there is no other field to leak,
 * but the response DTO is deliberately shaped to make that structural, not incidental).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BidHistoryApiTest {

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
    void bidHistoryRequiresNoAuthentication() throws Exception {
        String auctionId = seedAuction();
        seedBid(auctionId, "bidder-1", "150.00");

        mockMvc.perform(get("/api/auctions/{auctionId}/bids", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].bidderId").value("bidder-1"))
                .andExpect(jsonPath("$.content[0].amount").value(150.00))
                .andExpect(jsonPath("$.content[0].placedAt").exists());
    }

    @Test
    void bidHistoryContainsNoContactDetail() throws Exception {
        String auctionId = seedAuction();
        seedBid(auctionId, "bidder-1", "150.00");

        mockMvc.perform(get("/api/auctions/{auctionId}/bids", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].name").doesNotExist());
    }

    @Test
    void bidHistoryIsPagedNewestFirst() throws Exception {
        String auctionId = seedAuction();
        seedBid(auctionId, "bidder-1", "110.00");
        seedBid(auctionId, "bidder-2", "120.00");
        seedBid(auctionId, "bidder-1", "130.00");

        mockMvc.perform(get("/api/auctions/{auctionId}/bids", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].amount").value(130.00))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void bidHistoryOnAnAuctionWithNoBidsIsAnEmptyPage() throws Exception {
        String auctionId = seedAuction();

        mockMvc.perform(get("/api/auctions/{auctionId}/bids", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void bidHistoryOnAnUnknownAuctionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/auctions/{auctionId}/bids", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    private String seedAuction() {
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
        Bid bid = new Bid();
        bid.setAuctionId(auctionId);
        bid.setBidderId(bidderId);
        bid.setAmount(new BigDecimal(amount));
        bidRepository.saveAndFlush(bid);
    }
}
