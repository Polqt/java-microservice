package com.auction.notificationservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DealCreatedEvent(
        UUID eventId,
        String dealId,
        String auctionId,
        String sellerId,
        String winningBidderId,
        String winningBidId,
        BigDecimal finalPrice,
        LocalDateTime createdAt
) {}
