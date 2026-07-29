package com.auction.agentservice.proxybidder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProxyBidderResponse(
        String id,
        String auctionId,
        BigDecimal budget,
        ProxyBidderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
