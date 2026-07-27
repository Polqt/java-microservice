package com.auction.auctionservice.exception;

public class AuctionCloseTooEarlyException extends RuntimeException {
    public AuctionCloseTooEarlyException(String message) {
        super("Auction close too early: " + message);
    }
}
