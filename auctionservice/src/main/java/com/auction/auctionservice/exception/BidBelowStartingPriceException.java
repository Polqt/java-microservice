package com.auction.auctionservice.exception;

public class BidBelowStartingPriceException extends RuntimeException {
    public BidBelowStartingPriceException(String auctionId) {
        super("Bid is below starting price for Auction: " + auctionId);
    }
}