package com.auction.auctionservice.exception;

public class BidsAlreadyPlacedException extends RuntimeException {
    public BidsAlreadyPlacedException(String message) {
        super(message);
    }
}
