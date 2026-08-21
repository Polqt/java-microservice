package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, String> {

    /** Public browse — one status at a time, so the default (OPEN) is a plain equality query. */
    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);
}
