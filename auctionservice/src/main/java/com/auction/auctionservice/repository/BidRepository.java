package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, String> {
    Optional<Bid> findTopByAuctionIdOrderByAmountDescPlacedAtAsc(String auctionId);
}
