package com.auction.auctionservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
@Data
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String auctionId;
    private String bidderId;
    private BigDecimal amount;

    @CreationTimestamp
    private LocalDateTime placedAt;
}
