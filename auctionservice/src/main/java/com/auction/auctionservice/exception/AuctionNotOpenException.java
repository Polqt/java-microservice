package com.auction.auctionservice.exception;

public class AuctionNotOpenException extends RuntimeException {
    public AuctionNotOpenException(String message) {
        super("Auction not open: " + message);
    }
}
