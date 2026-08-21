package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.DealResponse;
import com.auction.auctionservice.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
public class DealController {

    private final AuctionService auctionService;

    /** Identity comes from the token; the service checks it against the Deal's two parties. */
    @GetMapping("/{dealId}")
    public DealResponse getDeal(@PathVariable String dealId, @AuthenticationPrincipal Jwt jwt) {
        return auctionService.getDeal(dealId, jwt.getSubject());
    }
}
