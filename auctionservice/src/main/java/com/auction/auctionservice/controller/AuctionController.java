package com.auction.auctionservice.controller;

import com.auction.auctionservice.dto.BidResponse;
import com.auction.auctionservice.dto.PlaceBidRequest;
import com.auction.auctionservice.service.AuctionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

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

}
