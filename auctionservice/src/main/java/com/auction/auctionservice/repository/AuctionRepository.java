package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, String> {
}
