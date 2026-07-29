package com.auction.agentservice.proxybidder;

public class BudgetBelowStartingPriceException extends RuntimeException {
    public BudgetBelowStartingPriceException(String id) {
        super(
                "Budget is below the Auction starting price for Bidder: " + id
        );
    }
}
