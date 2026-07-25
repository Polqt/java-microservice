package com.auction.auctionservice.exception;

public class BidTooLowException extends RuntimeException {
    public BidTooLowException(String message) {
        super("Bid too low: " + message);
    }
}
