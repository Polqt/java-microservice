package com.auction.auctionservice.exception;

public class SellerCannotBidException extends RuntimeException {
    public SellerCannotBidException(String message) {
        super("Seller cannot bid on own auction: " + message);
    }
}
