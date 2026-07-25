package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, String> {}
