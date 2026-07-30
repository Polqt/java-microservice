package com.auction.agentservice.proxybidder;

public class OptimisticLockingFailureException extends RuntimeException {
    public OptimisticLockingFailureException(String message) {
        super(
                "Optimistic locking failure: " + message
        );
    }
}
