package com.auction.auctionservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BidResponse {
    private String bidId;
    private String auctionId;
    private BigDecimal amount;
    private BigDecimal currentPrice;
    private boolean leading;
    private LocalDateTime placedAt;
}
