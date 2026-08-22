package com.auction.auctionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per Auction a Bidder has bid on: their own best Bid there, and whether it still leads. */
public record MyBidSummary(
        String auctionId,
        BigDecimal amount,
        LocalDateTime placedAt,
        boolean leading
) {
}
