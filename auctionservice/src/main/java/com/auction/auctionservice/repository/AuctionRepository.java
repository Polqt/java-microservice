package com.auction.auctionservice.repository;

import com.auction.auctionservice.model.Auction;
import com.auction.auctionservice.model.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface AuctionRepository extends JpaRepository<Auction, String> {

    /**
     * Public browse's candidate set. Bounded by the Pageable's size, not paginated
     * as a true Page — the service filters by effective (time-derived) status
     * afterward, at which point a DB-level total-elements count would be wrong.
     */
    List<Auction> findByStatusIn(Set<AuctionStatus> statuses, Pageable pageable);

    /** A Seller's own Auctions — every status, no effective-status filtering, real DB pagination. */
    Page<Auction> findBySellerIdOrderByCreatedAtDesc(String sellerId, Pageable pageable);
}
