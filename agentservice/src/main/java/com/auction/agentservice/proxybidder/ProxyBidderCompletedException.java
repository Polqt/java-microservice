package com.auction.agentservice.proxybidder;

public class ProxyBidderCompletedException extends RuntimeException {
    public ProxyBidderCompletedException(String id) {
        super(
                "Completed Proxy Bidder cannot be modified: " + id
        );
    }
}
