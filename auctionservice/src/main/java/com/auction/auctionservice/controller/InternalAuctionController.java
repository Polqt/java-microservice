package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.AuctionBidStateResponse;
import com.auction.auctionservice.dto.BidResponse;
import com.auction.auctionservice.dto.InternalPlaceBidRequest;
import com.auction.auctionservice.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service API for agentservice. Not routed through the gateway
 * (which only forwards /api/**), and gated on the SERVICE_AGENT realm role
 * held by the agentservice service account.
 */
@RestController
@RequestMapping("/internal/auctions")
@RequiredArgsConstructor
@PreAuthorize(
        "principal.claims['azp'] == 'agentservice' and " +
        "principal.claims['aud'].contains('auctionservice')"
)
public class InternalAuctionController {

    private final AuctionService auctionService;

    @GetMapping("/{auctionId}/bid-state")
    public AuctionBidStateResponse getBidState(@PathVariable String auctionId) {
        return auctionService.getBidState(auctionId);
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<BidResponse> placeBid(
            @PathVariable String auctionId,
            @Valid @RequestBody InternalPlaceBidRequest request) {

        BidResponse response = auctionService.placeBid(
                auctionId,
                request.bidderId(),
                request.amount(),
                request.reactionId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
