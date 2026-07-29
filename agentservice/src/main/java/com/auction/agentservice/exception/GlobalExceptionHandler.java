package com.auction.agentservice.exception;

import com.auction.agentservice.proxybidder.BudgetBelowStartingPriceException;
import com.auction.agentservice.proxybidder.ProxyBidderAlreadyExistsException;
import com.auction.agentservice.proxybidder.ProxyBidderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProxyBidderAlreadyExistsException.class)
    public ProblemDetail handleAlreadyExists(
            ProxyBidderAlreadyExistsException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    @ExceptionHandler(ProxyBidderNotFoundException.class)
    public ProblemDetail handleNotFound(
            ProxyBidderNotFoundException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    @ExceptionHandler(BudgetBelowStartingPriceException.class)
    public ProblemDetail handleBudgetBelowStartingPrice(
            BudgetBelowStartingPriceException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getMessage()
        );
    }
}