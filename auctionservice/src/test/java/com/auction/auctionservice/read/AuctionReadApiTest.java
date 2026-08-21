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

    /**
     * The read side of the same bug `bidSucceedsOnceAScheduledAuctionsStartTimeHasPassed`
     * locks down for bidding: the stored status never flips from SCHEDULED to OPEN,
     * so the read must derive effective status rather than reporting the raw column.
     * A ticket-03 requirement — this and bid acceptance must never disagree.
     */
    @Test
    void aScheduledAuctionPastItsStartTimeReadsAsOpen() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.SCHEDULED);
        auction.setStartAt(LocalDateTime.now().minusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(1));
        String auctionId = auctionRepository.saveAndFlush(auction).getId();

        mockMvc.perform(get("/api/auctions/{auctionId}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void aScheduledAuctionBeforeItsStartTimeStillReadsAsScheduled() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.SCHEDULED);
        auction.setStartAt(LocalDateTime.now().plusHours(1));
        auction.setEndAt(LocalDateTime.now().plusHours(2));
        String auctionId = auctionRepository.saveAndFlush(auction).getId();

        mockMvc.perform(get("/api/auctions/{auctionId}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    /**
     * The gap a code review found: effectiveStatus checked startAt but not endAt, so
     * an Auction whose window had ended without a manual Close still read OPEN via
     * GET while placeBid correctly rejected a Bid against it — exactly the
     * disagreement ticket 03 forbids. Locks down the fix (both now derive from the
     * same shared method).
     */
    @Test
    void anAuctionPastItsEndTimeReadsAsClosedEvenWithoutAManualClose() throws Exception {
        Auction auction = new Auction();
        auction.setSellerId(SELLER_ID);
        auction.setTitle("Vintage film camera");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("10.00"));
        auction.setStatus(AuctionStatus.OPEN);
        auction.setStartAt(LocalDateTime.now().minusHours(2));
        auction.setEndAt(LocalDateTime.now().minusMinutes(1));
        String auctionId = auctionRepository.saveAndFlush(auction).getId();

        mockMvc.perform(get("/api/auctions/{auctionId}", auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
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

        // seedAuction always gives a past startAt, which would make a "SCHEDULED"
        // auction here actually effectively open (its window has started) — not what
        // this test means by "still scheduled". A genuinely scheduled Auction needs
        // a future startAt, so it is seeded directly instead.
        Auction stillScheduled = new Auction();
        stillScheduled.setSellerId(SELLER_ID);
        stillScheduled.setTitle("Not yet started");
        stillScheduled.setStartingPrice(new BigDecimal("100.00"));
        stillScheduled.setCurrentPrice(new BigDecimal("100.00"));
        stillScheduled.setMinIncrement(new BigDecimal("10.00"));
        stillScheduled.setStatus(AuctionStatus.SCHEDULED);
        stillScheduled.setStartAt(LocalDateTime.now().plusHours(1));
        stillScheduled.setEndAt(LocalDateTime.now().plusHours(2));
        auctionRepository.saveAndFlush(stillScheduled);

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(openId));
    }

    /**
     * The other gap the same review found: browseAuctions filtered on the raw
     * stored column, so a SCHEDULED Auction whose window had already opened —
     * genuinely biddable, and reported OPEN by the single-item read — never
     * appeared under the default OPEN browse filter at all. Locks down the fix
     * (browse now widens to every raw status that could resolve to OPEN, then
     * filters by the same effectiveStatus the single read and placeBid use).
     */
    @Test
    void browsingForOpenIncludesAScheduledAuctionPastItsStartTime() throws Exception {
        Auction scheduledButStarted = new Auction();
        scheduledButStarted.setSellerId(SELLER_ID);
        scheduledButStarted.setTitle("Scheduled, but its window already opened");
        scheduledButStarted.setStartingPrice(new BigDecimal("100.00"));
        scheduledButStarted.setCurrentPrice(new BigDecimal("100.00"));
        scheduledButStarted.setMinIncrement(new BigDecimal("10.00"));
        scheduledButStarted.setStatus(AuctionStatus.SCHEDULED);
        scheduledButStarted.setStartAt(LocalDateTime.now().minusMinutes(1));
        scheduledButStarted.setEndAt(LocalDateTime.now().plusHours(1));
        String scheduledButStartedId = auctionRepository.saveAndFlush(scheduledButStarted).getId();

        seedAuction(AuctionStatus.CLOSED);

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(scheduledButStartedId))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"));
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
