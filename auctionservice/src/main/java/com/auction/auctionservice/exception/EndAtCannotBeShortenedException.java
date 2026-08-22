package com.auction.auctionservice.exception;

public class EndAtCannotBeShortenedException extends RuntimeException {
    public EndAtCannotBeShortenedException(String message) {
        super(message);
    }
}
