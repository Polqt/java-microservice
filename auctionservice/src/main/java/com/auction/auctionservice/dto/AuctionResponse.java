package com.auction.auctionservice.dto;

import com.auction.auctionservice.model.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionResponse(
        String id,
        String sellerId,
        String title,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minIncrement,
        AuctionStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
