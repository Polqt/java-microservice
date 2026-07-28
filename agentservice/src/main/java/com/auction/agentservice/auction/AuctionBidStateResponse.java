package com.auction.agentservice.auction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionBidStateResponse(
        String auctionId,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String highestBidderId
) {
}
