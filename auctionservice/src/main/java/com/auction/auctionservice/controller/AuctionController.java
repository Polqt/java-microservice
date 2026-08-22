package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.*;
import com.auction.auctionservice.model.AuctionStatus;
import com.auction.auctionservice.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    /** Public — no Authorization header required. Defaults to OPEN Auctions. */
    @GetMapping
    public ResponseEntity<AuctionPageResponse> browseAuctions(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionService.browseAuctions(status, page, size));
    }

    /** Public — no Authorization header required. */
    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionResponse> getAuction(@PathVariable String auctionId) {
        return ResponseEntity.ok(auctionService.getAuction(auctionId));
    }

    /** Public — no Authorization header required. Bidder identifier, amount, timestamp only. */
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<PageResponse<BidHistoryItem>> getBidHistory(
            @PathVariable String auctionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionService.getBidHistory(auctionId, page, size));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AuctionPageResponse> getMyAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(auctionService.getMyAuctions(jwt.getSubject(), page, size));
    }

    @PatchMapping("/{auctionId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AuctionResponse> updateAuction(
            @PathVariable String auctionId,
            @Valid @RequestBody UpdateAuctionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(auctionService.updateAuction(auctionId, jwt.getSubject(), request));
    }

    @PostMapping("/{auctionId}/cancel")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AuctionResponse> cancelAuction(
            @PathVariable String auctionId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(auctionService.cancelAuction(auctionId, jwt.getSubject()));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<BidResponse> placeBid(@PathVariable String auctionId, @Valid @RequestBody PlaceBidRequest request, @AuthenticationPrincipal Jwt jwt) {
        String bidderId = jwt.getSubject();

        BidResponse response = auctionService.placeBid(
                auctionId,
                bidderId,
                request.getAmount()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{auctionId}/close")
    public ResponseEntity<CloseAuctionResponse> closeAuction(
            @PathVariable String auctionId,
            @AuthenticationPrincipal Jwt jwt) {
        // Identity comes from the token; the service checks it against the auction's seller.
        return ResponseEntity.ok(auctionService.closeAuction(auctionId, jwt.getSubject()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<AuctionResponse> createAuction(
            @Valid @RequestBody CreateAuctionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuctionResponse response = auctionService.createAuction(jwt.getSubject(), request);
        return ResponseEntity
                .created(URI.create("/api/auctions/" + response.id()))
                .body(response);
    }

}
