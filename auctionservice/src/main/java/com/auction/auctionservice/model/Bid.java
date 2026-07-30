package com.auction.auctionservice.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// A Bid is immutable once placed — never updated, only inserted.
@Entity
@Table(
        name = "bids",
        indexes = {
                // Winner selection orders by (amount desc, placedAt asc) within one auction.
                // Without this the query is a full scan of every bid ever placed.
                @Index(name = "idx_bids_auction_amount", columnList = "auctionId, amount, placedAt"),
                @Index(name = "idx_bids_bidder", columnList = "bidderId")
        },
        uniqueConstraints = {
                // Idempotency guard for agent-placed Bids: a replayed reactionId cannot
                // insert a second Bid, even if the application-level check races.
                // NULL is allowed many times over in Postgres, so human bids are unaffected.
                @UniqueConstraint(name = "uk_bids_reaction", columnNames = "reactionId")
        }
)
@Data
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String auctionId;

    @Column(nullable = false)
    private String bidderId;

    @Column(nullable = false)
    private BigDecimal amount;

    /** Idempotency key supplied by agentservice. Null for human bids. */
    private String reactionId;

    @CreationTimestamp
    private LocalDateTime placedAt;
}
