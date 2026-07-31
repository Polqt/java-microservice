package com.auction.auctionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateAuctionRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull
        @Positive
        BigDecimal startingPrice,

        @NotNull
        @Positive
        BigDecimal minIncrement,

        @NotNull
        LocalDateTime startAt,

        @NotNull
        LocalDateTime endAt
) {
}
