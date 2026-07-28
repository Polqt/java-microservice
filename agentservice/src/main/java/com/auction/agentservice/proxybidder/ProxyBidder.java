package com.auction.agentservice.proxybidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProxyBidder(
        String id,
        Long version,
        String auctionId,
        String bidderId,
        BigDecimal budget,
        ProxyBidderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
