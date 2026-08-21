package com.auction.auctionservice.deal;

import com.auction.auctionservice.TestcontainersConfiguration;
import com.auction.auctionservice.model.Deal;
import com.auction.auctionservice.repository.DealRepository;
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
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behavior of the minimal Deal read — the two parties can see the outcome
 * they are part of; nobody else can, and a non-party gets 404, never 403,
 * so the response never confirms the Deal exists to someone with no right
 * to know (same rule as closeAuction's ownership check).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GetDealApiTest {

    private static final String SELLER_ID = "seller-1";
    private static final String WINNER_ID = "bidder-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealRepository dealRepository;

    private String dealId;

    @BeforeEach
    void seedDeal() {
        dealRepository.deleteAll();

        Deal deal = new Deal();
        deal.setAuctionId("auction-1");
        deal.setSellerId(SELLER_ID);
        deal.setWinningBidderId(WINNER_ID);
        deal.setWinningBidId("bid-1");
        deal.setFinalPrice(new BigDecimal("150.00"));
        dealId = dealRepository.saveAndFlush(deal).getId();
    }

    @Test
    void theWinningBidderCanReadTheDeal() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", dealId).with(user(WINNER_ID, "BIDDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dealId))
                .andExpect(jsonPath("$.sellerId").value(SELLER_ID))
                .andExpect(jsonPath("$.winningBidderId").value(WINNER_ID))
                .andExpect(jsonPath("$.finalPrice").value(150.00));
    }

    @Test
    void theSellerCanReadTheDeal() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", dealId).with(user(SELLER_ID, "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winningBidderId").value(WINNER_ID));
    }

    @Test
    void aLosingBidderCannotReadTheDeal() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", dealId).with(user("someone-else", "BIDDER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnrelatedSellerCannotReadTheDeal() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", dealId).with(user("another-seller", "SELLER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownDealIsNotFound() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", UUID.randomUUID().toString())
                        .with(user(WINNER_ID, "BIDDER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedReadIsRejected() throws Exception {
        mockMvc.perform(get("/api/deals/{dealId}", dealId))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor user(String subject, String role) {
        return jwt()
                .jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
