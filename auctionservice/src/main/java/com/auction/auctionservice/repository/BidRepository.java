package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, String> {

    /** Winning Bid: highest amount, earliest placed wins a tie. */
    Optional<Bid> findTopByAuctionIdOrderByAmountDescPlacedAtAsc(String auctionId);

    /** Idempotency lookup for agent-placed Bids. */
    Optional<Bid> findByReactionId(String reactionId);
}
