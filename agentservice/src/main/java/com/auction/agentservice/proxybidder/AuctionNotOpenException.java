package com.auction.agentservice.proxybidder;

public class AuctionNotOpenException extends RuntimeException {
    public AuctionNotOpenException(String message) {
        super(
                "Auction is not open: " + message
        );
    }
}
