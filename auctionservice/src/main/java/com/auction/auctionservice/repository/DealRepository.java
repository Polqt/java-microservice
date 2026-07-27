package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, String> {
    Optional<Deal> findByAuctionId(String auctionId);
}
