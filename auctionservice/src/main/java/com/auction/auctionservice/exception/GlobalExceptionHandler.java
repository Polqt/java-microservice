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

    @ExceptionHandler(BidBelowStartingPriceException.class)
    public ProblemDetail handleBidBelowStartingPrice(BidBelowStartingPriceException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(AuctionCloseTooEarlyException.class)
    public ProblemDetail handleAuctionCloseTooEarlyException(AuctionCloseTooEarlyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(403), ex.getMessage());
    }

    @ExceptionHandler(NotAuctionOwnerException.class)
    public ProblemDetail handleNotAuctionOwnerException(NotAuctionOwnerException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // A lost optimistic-lock race is a normal outcome under contention, not a server fault:
    // the bid (or close) was beaten to the row. The client should re-read and decide again.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Another request updated this auction first, re-read and retry"
        );
    }

    @ExceptionHandler(InvalidAuctionWindowException.class)
    public ProblemDetail handleInvalidAuctionWindowException(InvalidAuctionWindowException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
    }

    @ExceptionHandler(DealNotFoundException.class)
    public ProblemDetail handleDealNotFoundException(DealNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
