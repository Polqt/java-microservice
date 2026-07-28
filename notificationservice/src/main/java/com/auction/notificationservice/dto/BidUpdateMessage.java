package com.auction.notificationservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record BidUpdateMessage(
        UUID eventId,
        String auctionId,
        String bidId,
        BigDecimal amount,
        LocalDateTime timestamp
) {}
