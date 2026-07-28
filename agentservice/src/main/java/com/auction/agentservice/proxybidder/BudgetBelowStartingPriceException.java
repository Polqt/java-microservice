package com.auction.agentservice.proxybidder;

public class BudgetBelowStartingPriceException extends RuntimeException {
  public BudgetBelowStartingPriceException(String message) {
    super(message);
  }
}
