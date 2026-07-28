package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.AuctionBidStateResponse;
import com.auction.auctionservice.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auctions")
@RequiredArgsConstructor
public class InternalAuctionController {

    private final AuctionService auctionService;

    @GetMapping("/{auctionId}/bid-state")
    @PreAuthorize(
            "principal.claims['azp'] == 'agentservice' and " +
            "principal.claims['aud'].contains('auctionservice')"
    )
    public AuctionBidStateResponse getBidState(@PathVariable String auctionId) {
        return auctionService.getBidState(auctionId);
    }
}
