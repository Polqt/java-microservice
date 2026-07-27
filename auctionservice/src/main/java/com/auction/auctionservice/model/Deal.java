package com.auction.auctionservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals")
@Data
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String auctionId;
    private String sellerId;

    private String winningBidderId;
    private String winningBidId;
    private BigDecimal finalPrice;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
