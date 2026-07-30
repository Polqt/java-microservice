package com.auction.auctionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InternalPlaceBidRequest(
        @NotBlank
        String bidderId,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotBlank
        String reactionId
) {
}
