package com.auction.auctionservice.exception;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(String message) {
        super("Auction not found: " + message);
    }
}
