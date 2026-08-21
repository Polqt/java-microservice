package com.auction.auctionservice.read;

import com.auction.auctionservice.TestcontainersConfiguration;
import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.repository.AuctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavior of public Auction browse and read — the endpoints a Bidder reaches
 * before signing in. Both must work with no Authorization header at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuctionReadApiTest {

    private static final String SELLER_ID = "seller-1";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private AuctionRepository auctionRepository;

    @BeforeEach
    void resetState() {
        auctionRepository.deleteAll();
    }

    @Test
    void readingAnAuctionRequiresNoAuthentication() throws Exception {
        String auctionId = seedAuction(AuctionStatus.OPEN);

        mockMvc.perform(get("/api/auctions/{auctionId}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auctionId))
                .andExpect(jsonPath("$.startingPrice").value(100.00))
                .andExpect(jsonPath("$.currentPrice").value(100.00))
                .andExpect(jsonPath("$.minIncrement").value(10.00))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.startAt").exists())
                .andExpect(jsonPath("$.endAt").exists());
    }

    @Test
    void readingAnUnknownAuctionIsNotFound() throws Exception {
        mockMvc.perform(get("/api/auctions/{auctionId}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void browsingDefaultsToOpenAuctionsOnly() throws Exception {
        String openId = seedAuction(AuctionStatus.OPEN);
        seedAuction(AuctionStatus.CLOSED);
        seedAuction(AuctionStatus.SCHEDULED);

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(openId));
    }

    @Test
    void aStatusFilterWidensBrowsingBeyondOpen() throws Exception {
        seedAuction(AuctionStatus.OPEN);
        String closedId = seedAuction(AuctionStatus.CLOSED);

        mockMvc.perform(get("/api/auctions").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(closedId));
    }

    @Test
    void browsingRequiresNoAuthentication() throws Exception {
        seedAuction(AuctionStatus.OPEN);

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk());
    }

    @Test
    void pageSizeIsBoundedRegardlessOfWhatTheClientRequests() throws Exception {
        seedAuction(AuctionStatus.OPEN);

        // The client asks for 1000; the server must never hand back more than its cap.
        mockMvc.perform(get("/api/auctions").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    private String seedAuction(AuctionStatus status) {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(status);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));
        return auctionRepository.saveAndFlush(auction).getId();
    }
}
