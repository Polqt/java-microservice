package com.auction.auctionservice.exception;

public class NotAuctionOwnerException extends RuntimeException {
    public NotAuctionOwnerException(String auctionId) {
        super("Only the seller may close this auction: " + auctionId);
    }
}
