package com.auction.agentservice.proxybidder;

public class ProxyBidderAlreadyExistsException extends RuntimeException {
  public ProxyBidderAlreadyExistsException(String message) {
    super(message);
  }
}
