package com.auction.auctionservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BidPlacedEvent(
        UUID eventId,
        String auctionId,
        String bidId,
        String bidderId,
        BigDecimal amount,
        LocalDateTime timestamp
) {}
