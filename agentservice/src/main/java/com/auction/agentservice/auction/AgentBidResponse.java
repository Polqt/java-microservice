package com.auction.agentservice.auction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentBidResponse(
        String bidId,
        String auctionId,
        BigDecimal amount,
        BigDecimal currentPrice,
        boolean leading,
        LocalDateTime placedAt
) {
}
