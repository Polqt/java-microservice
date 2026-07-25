package com.auction.auctionservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PlaceBidRequest {

    @NotNull
    @Positive
    private BigDecimal amount;
}
