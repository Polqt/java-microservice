package com.auction.auctionservice.model;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "auctions")
@Data
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    private String sellerId;

    private String title;

    private BigDecimal startingPrice;
    private BigDecimal currentPrice;
    private BigDecimal minIncrement;

    @Nullable
    private String highestBidderId;

    @Enumerated(EnumType.STRING)
    private AuctionStatus status;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @Nullable
    private LocalDateTime closedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
