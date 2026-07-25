package com.auction.auctionservice.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuctionNotFoundException.class)
    public ProblemDetail handleAuctionNotFoundException(AuctionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(404), ex.getMessage());
    }

    @ExceptionHandler(SellerCannotBidException.class)
    public ProblemDetail handleSellerCannotBidException(SellerCannotBidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
    }

    @ExceptionHandler(AuctionNotOpenException.class)
    public ProblemDetail handleAuctionNotOpenException(AuctionNotOpenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
    }

    @ExceptionHandler(BidTooLowException.class)
    public ProblemDetail handleBidTooLowException(BidTooLowException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(409), ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Another bid was accepted first");
    }

    @ExceptionHandler(BidBelowStartingPriceException.class)
    public ProblemDetail handleBidBelowStartingPrice(BidBelowStartingPriceException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getMessage()
        );
    }
}
