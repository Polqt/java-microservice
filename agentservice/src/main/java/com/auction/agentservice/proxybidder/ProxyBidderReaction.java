package com.auction.agentservice.proxybidder;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "proxy_bidder_reactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reaction_proxy_source",
                columnNames = {
                        "proxy_bidder_id",
                        "source_event_id"
                }
        )
)
public class ProxyBidderReaction {

    @Id
    @Column(name = "reaction_id", length = 100)
    private String reactionId;

    @Version
    private Long version;

    @Column(name = "proxy_bidder_id", nullable = false)
    private String proxyBidderId;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "auction_id", nullable = false)
    private String auctionId;

    @Column(precision = 19, scale = 2)
    private BigDecimal proposedAmount;

    @Column(nullable = false)
    private int attemptCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProxyBidderReactionOutcome outcome;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ProxyBidderReaction() {}

    public ProxyBidderReaction(
            String reactionId,
            String proxyBidderId,
            UUID sourceEventId,
            String auctionId,
            BigDecimal proposedAmount
    ) {
        this.reactionId = reactionId;
        this.proxyBidderId = proxyBidderId;
        this.sourceEventId = sourceEventId;
        this.auctionId = auctionId;
        this.proposedAmount = proposedAmount;
        this.attemptCount = 0;
        this.outcome = ProxyBidderReactionOutcome.PENDING;
    }

    public void recordAttempt() {
        this.attemptCount++;
    }

    public void markSucceeded() {
        this.outcome = ProxyBidderReactionOutcome.SUCCEEDED;
    }

    public void markBalked() {
        this.outcome = ProxyBidderReactionOutcome.BALKED;
    }

    public void markSkipped() {
        this.outcome = ProxyBidderReactionOutcome.SKIPPED;
    }

    public void markFailed() {
        this.outcome = ProxyBidderReactionOutcome.FAILED;
    }

    public String getReactionId() {
        return reactionId;
    }

    public Long getVersion() {
        return version;
    }

    public String getProxyBidderId() {
        return proxyBidderId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public BigDecimal getProposedAmount() {
        return proposedAmount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public ProxyBidderReactionOutcome getOutcome() {
        return outcome;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void recordProposal(BigDecimal proposedAmount) {
        this.proposedAmount = proposedAmount;
    }
}
