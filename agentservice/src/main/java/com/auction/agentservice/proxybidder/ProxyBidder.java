package com.auction.agentservice.proxybidder;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "proxy_bidders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_proxy_bidder_auction_bidder",
                columnNames = {"auction_id", "bidder_id"}
        )
)
public class ProxyBidder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @Column(name = "auction_id", nullable = false)
    private String auctionId;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProxyBidderStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ProxyBidder() {}

    public ProxyBidder(
            String auctionId,
            String bidderId,
            BigDecimal budget
    ) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.budget = budget;
        this.status = ProxyBidderStatus.ACTIVE;
    }


    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public void setStatus(ProxyBidderStatus status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public ProxyBidderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
