package com.auction.agentservice.proxybidder;

public class ProxyBidderNotFoundException extends RuntimeException {
    public ProxyBidderNotFoundException(String message) {
        super(
                "ProxyBidder not found " + message
        );
    }
}
