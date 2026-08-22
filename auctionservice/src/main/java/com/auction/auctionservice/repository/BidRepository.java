package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, String> {

    /** Winning Bid: highest amount, earliest placed wins a tie. */
    Optional<Bid> findTopByAuctionIdOrderByAmountDescPlacedAtAsc(String auctionId);

    /** Idempotency lookup for agent-placed Bids. */
    Optional<Bid> findByReactionId(String reactionId);

    /** Public bid history for one Auction, newest first. Real DB pagination — Bid rows never change effective meaning after the fact. */
    Page<Bid> findByAuctionIdOrderByPlacedAtDesc(String auctionId, Pageable pageable);

    /** A Bid exists at all — gates edit/cancel rules that only apply once a contest has started. */
    boolean existsByAuctionId(String auctionId);

    /**
     * Bounded candidate set for "my bids": a Bidder may have placed several Bids across
     * several Auctions, so this is reduced to one row per Auction in the service, the
     * same bounded-then-filter-in-Java pattern browseAuctions already uses.
     */
    List<Bid> findByBidderIdOrderByPlacedAtDesc(String bidderId, Pageable pageable);
}
