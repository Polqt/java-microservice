package com.auction.auctionservice.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A partial patch: any field left null means "leave this alone". Bean Validation
 * annotations here only fire when a value is actually present, which is what
 * partial-patch semantics need.
 */
public record UpdateAuctionRequest(
        @Size(max = 200)
        String title,

        @Positive
        BigDecimal startingPrice,

        @Positive
        BigDecimal minIncrement,

        LocalDateTime endAt
) {
}
