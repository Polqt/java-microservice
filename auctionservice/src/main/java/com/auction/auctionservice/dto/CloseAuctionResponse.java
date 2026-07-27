package com.auction.auctionservice.dto;

import com.auction.auctionservice.model.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CloseAuctionResponse(
    String auctionId,
    AuctionStatus status,
    LocalDateTime closedAt,
    String dealId,
    String winningBidderId,
    BigDecimal finalPrice
) {}
