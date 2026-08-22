package com.auction.auctionservice.exception;

/**
 * -> HTTP 404. Also thrown — deliberately, not a 403 — when the Deal exists
 * but the caller is neither the Seller nor the winning Bidder: a 403 would
 * confirm the Deal's existence to someone with no right to know, same rule
 * closeAuction, updateAuction, and cancelAuction's own ownership checks use.
 */
public class DealNotFoundException extends RuntimeException {
    public DealNotFoundException(String dealId) {
        super("Deal not found: " + dealId);
    }
}
