package com.auction.agentservice.proxybidder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProxyBidderRepository extends JpaRepository<ProxyBidder, String> {
    boolean existsByAuctionIdAndBidderId(
            String auctionId,
            String bidderId
    );

    Optional<ProxyBidder> findByIdAndBidderId(
            String id,
            String bidderId
    );

    List<ProxyBidder> findAllByAuctionId(
            String auctionId
    );

    List<ProxyBidder> findAllByAuctionIdAndStatus(
            String auctionId,
            ProxyBidderStatus status
    );
}
