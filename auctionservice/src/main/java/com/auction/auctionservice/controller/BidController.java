package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.MyBidSummary;
import com.auction.auctionservice.dto.PageResponse;
import com.auction.auctionservice.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bids")
@RequiredArgsConstructor
public class BidController {

    private final AuctionService auctionService;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BIDDER')")
    public ResponseEntity<PageResponse<MyBidSummary>> getMyBids(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(auctionService.getMyBids(jwt.getSubject(), page, size));
    }
}
