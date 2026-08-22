package com.auction.auctionservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public bid history row — bidder identifier, amount, timestamp only. No contact detail exists to leak. */
public record BidHistoryItem(
        String bidderId,
        BigDecimal amount,
        LocalDateTime placedAt
) {
}
